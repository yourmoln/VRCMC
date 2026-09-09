import java.util.Properties
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.application); alias(libs.plugins.compose); alias(libs.plugins.kotlin.compose) }

val kuromojiIpadicSource by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies { add(kuromojiIpadicSource.name, libs.kuromoji.ipadic) }
val kuromojiIpadicArchive =
    kuromojiIpadicSource.elements.map { artifacts -> artifacts.single().asFile }
val stripKuromojiDictionary by tasks.registering(Zip::class) {
    archiveFileName.set("kuromoji-ipadic-runtime-${libs.versions.kuromoji.get()}.jar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/kuromoji"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(kuromojiIpadicArchive.map(::zipTree)) {
        exclude("com/atilika/kuromoji/ipadic/*.bin")
        exclude("com/atilika/kuromoji/ipadic/compile/**")
        exclude("META-INF/maven/**")
    }
}
val kuromojiIpadicRuntime =
    files(stripKuromojiDictionary.flatMap { it.archiveFile }).builtBy(stripKuromojiDictionary)

val isWindowsX64 = providers.systemProperty("os.name").get().startsWith("Windows", ignoreCase = true) &&
    providers.systemProperty("os.arch").get().lowercase() in setOf("amd64", "x86_64")

// These two dependencies have no transitive runtime dependencies. Keep their Java
// classes, JNI resource paths and notices, but omit other platforms and debug symbols.
// Use the original artifacts on other hosts so desktop development remains portable.
fun desktopNativeRuntime(
    taskName: String,
    dependency: Provider<MinimalExternalModuleDependency>,
    vararg excludedPaths: String,
): Any {
    if (!isWindowsX64) return dependency
    val source = configurations.create("${taskName}Source") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(source.name, dependency)
    val archive = source.elements.map { it.single().asFile }
    val stripped = tasks.register<Zip>(taskName) {
        archiveFileName.set(archive.map { "${it.nameWithoutExtension}-windows-x64.jar" })
        destinationDirectory.set(layout.buildDirectory.dir("generated/windows-native"))
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        from(archive.map(::zipTree)) { exclude(*excludedPaths) }
    }
    return files(stripped.flatMap { it.archiveFile }).builtBy(stripped)
}

val desktopOnnxRuntime = desktopNativeRuntime(
    "stripOnnxRuntimeNatives", libs.onnxruntime,
    "ai/onnxruntime/native/linux-*/**",
    "ai/onnxruntime/native/osx-*/**",
    "ai/onnxruntime/native/**/*.pdb",
)
val desktopWhisperRuntime = desktopNativeRuntime(
    "stripWhisperNatives", libs.whisper.jni,
    "debian-*/**", "macos-*/**",
)

kotlin { jvm("desktop"); androidTarget(); iosX64(); iosArm64(); iosSimulatorArm64(); sourceSets {
    commonMain.dependencies {
        implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.materialIconsExtended); implementation(compose.ui); implementation(compose.components.resources)
        implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.compose.get()}")
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.websockets)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
    named("desktopMain") {
        kotlin.srcDir("src/jvmMain/kotlin")
        dependencies { implementation(libs.jlayer); implementation(desktopOnnxRuntime); implementation(desktopWhisperRuntime) }
        dependencies {
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.openvr)
            implementation(libs.lwjgl.opengl)
            implementation(libs.lwjgl.glfw)
            runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl-openvr:${libs.versions.lwjgl.get()}:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl-opengl:${libs.versions.lwjgl.get()}:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl.get()}:natives-windows")
        }
        dependencies { implementation(compose.desktop.currentOs); implementation(libs.ktor.client.cio); implementation(libs.jna.platform); implementation(libs.kuromoji.core); implementation(kuromojiIpadicRuntime); implementation(libs.wanakana.core) }
    }
    named("desktopTest").dependencies {
        implementation(libs.kuromoji.ipadic)
        implementation(compose.desktop.uiTestJUnit4)
    }
    androidMain {
        kotlin.srcDir("src/jvmMain/kotlin")
        dependencies { implementation(libs.activity.compose); implementation(libs.ktor.client.cio); implementation(libs.ktor.client.okhttp); implementation(libs.kuromoji.core); implementation(kuromojiIpadicRuntime); implementation(libs.wanakana.core) }
    }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
} }
android {
    packaging {
        resources {
            pickFirsts +=
                setOf(
                    "META-INF/CONTRIBUTORS.md",
                    "META-INF/LICENSE.md",
                    "META-INF/NOTICE.md",
                )
        }
    }

    applicationVariants.all {
        outputs.all {
            val apkName = "${rootProject.name}-v$versionName.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
        }
    }

    namespace = libs.versions.app.packageName.get()
    compileSdk = 36

    defaultConfig {
        applicationId = libs.versions.app.packageName.get()
        minSdk = 26
        targetSdk = 36
        versionCode = libs.versions.app.code.get().toInt()
        versionName = libs.versions.app.version.get()
    }

    val signingProperties = Properties()
    val signingPropertiesFile = rootProject.file("local.properties")
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(signingProperties::load)
    }

    val releaseStoreFile = signingProperties.getProperty("store_file")
        ?.takeIf(String::isNotBlank)
        ?.let(rootProject::file)

    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingProperties.getProperty("store_pass")
                keyAlias = signingProperties.getProperty("key_alias")
                keyPassword = signingProperties.getProperty("key_pass")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
compose.desktop {
    application {
        mainClass = "com.vrcmc.app.DesktopMainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            // LWJGL uses sun.misc.Unsafe for its native buffers and function bindings.
            modules("jdk.unsupported")
            targetFormats(TargetFormat.Exe)
            packageName = "VRCMC"
            packageVersion = libs.versions.app.version.get()
            description = "VRChat Chatbox assistant"
            vendor = "VRCM Team"
            windows {
                iconFile.set(project.file("src/desktopMain/resources/VRCMC.ico"))
                menuGroup = "VRCMC"
                upgradeUuid = "6fe18fbc-6e62-4d4e-8f4f-3b44cedf45ed"
            }
        }
    }
}
compose.resources { packageOfResClass = "com.vrcmc.app.generated.resources" }
