import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.wumin.wuminpy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wumin.wuminpy"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProperties = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            load(localPropsFile.inputStream())
        }
    }

    val propKeystoreFilePath = localProperties.getProperty("KEYSTORE_FILE_PATH") ?: "debug.keystore"
    val propKeystorePassword = localProperties.getProperty("KEYSTORE_PASSWORD") ?: "android"
    val propKeyAlias = localProperties.getProperty("KEY_ALIAS") ?: "AndroidDebugKey"
    val propKeyPassword = localProperties.getProperty("KEY_PASSWORD") ?: "android"

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(propKeystoreFilePath)
            storePassword = propKeystorePassword
            keyAlias = propKeyAlias
            keyPassword = propKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            storeFile = rootProject.file(propKeystoreFilePath)
            storePassword = propKeystorePassword
            keyAlias = propKeyAlias
            keyPassword = propKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    resourcePrefix = "app_"
    aaptOptions.ignoreAssetsPattern = ".git"

    kotlinOptions {
        jvmTarget = "1.8"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ========================================
    // 闭源模块 AAR（从私有仓库构建后放入 libs/）
    // 构建方法见 build_aar.sh
    // ========================================
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // 开源模块
    implementation(project(":auto"))

    // 第三方依赖
    implementation("com.github.getActivity:DeviceCompat:2.3")
    implementation("com.github.getActivity:XXPermissions:28.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("org.tukaani:xz:1.9")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.animation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
