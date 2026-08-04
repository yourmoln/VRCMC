import java.util.Properties

plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.application); alias(libs.plugins.compose); alias(libs.plugins.kotlin.compose) }
kotlin { jvm("desktop"); androidTarget(); iosX64(); iosArm64(); iosSimulatorArm64(); sourceSets {
    commonMain.dependencies {
        implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.materialIconsExtended); implementation(compose.ui); implementation(compose.components.resources)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.core)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
    named("desktopMain").dependencies { implementation(compose.desktop.currentOs); implementation(libs.ktor.client.cio) }
    androidMain.dependencies { implementation(libs.activity.compose); implementation(libs.ktor.client.cio) }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
} }
android {
    applicationVariants.all {
        outputs.all {
            val apkName = "${rootProject.name}-v$versionName.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
        }
    }

    namespace = libs.versions.app.packageName.get()
    compileSdk = 35

    defaultConfig {
        applicationId = libs.versions.app.packageName.get()
        minSdk = 26
        targetSdk = 35
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
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
compose.desktop { application { mainClass = "com.vrcmc.app.DesktopMainKt" } }
compose.resources { packageOfResClass = "com.vrcmc.app.generated.resources" }
