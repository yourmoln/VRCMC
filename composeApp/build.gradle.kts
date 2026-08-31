import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.application); alias(libs.plugins.compose); alias(libs.plugins.kotlin.compose) }
kotlin { jvm("desktop"); androidTarget(); iosX64(); iosArm64(); iosSimulatorArm64(); sourceSets {
    commonMain.dependencies {
        implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.materialIconsExtended); implementation(compose.ui); implementation(compose.components.resources)
        implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.compose.get()}")
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.core)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
    named("desktopMain").dependencies { implementation(compose.desktop.currentOs); implementation(libs.ktor.client.cio); implementation(libs.jna.platform); implementation(libs.kuromoji.ipadic); implementation(libs.wanakana.core) }
    androidMain.dependencies { implementation(libs.activity.compose); implementation(libs.ktor.client.cio); implementation(libs.kuromoji.ipadic); implementation(libs.wanakana.core) }
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-kuromoji.pro",
            )
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
            configurationFiles.from(
                project.file("proguard-rules.pro"),
                project.file("proguard-kuromoji.pro"),
            )
        }
        nativeDistributions {
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
