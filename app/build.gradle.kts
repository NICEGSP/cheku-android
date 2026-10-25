import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 服务端配置不入库：从 Gradle 属性（-P…、~/.gradle/gradle.properties）或 local.properties 读取。
// 都没配置时 App 以纯本地模式运行，联网功能自动关闭。
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun configValue(name: String): String =
    (providers.gradleProperty(name).orNull ?: localProperties.getProperty(name) ?: "").trim()

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val officialWebsiteUrl = configValue("CHEKU_WEBSITE_URL").trimEnd('/')
val runtimeConfigPublicKey = configValue("CHEKU_CONFIG_PUBLIC_KEY")
val certificatePins = configValue("CHEKU_CERT_PINS")

// 可选的发布签名：secrets/keystore.properties（storeFile / storePassword / keyAlias / keyPassword，
// storeFile 相对 app 模块目录）。没有它时 debug 用默认调试证书，release 输出未签名包。
val signingProperties: Map<String, String>? = rootProject.file("secrets/keystore.properties")
    .takeIf { it.isFile }
    ?.readLines()
    ?.mapNotNull { line ->
        line.substringBefore('=').trim().takeIf { it.isNotEmpty() && !it.startsWith("#") }?.let { key ->
            key to line.substringAfter('=', "").trim()
        }
    }
    ?.toMap()

android {
    namespace = "com.cheku.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.cheku.recovery"
        minSdk = 23
        targetSdk = 37
        versionCode = 913
        versionName = "v1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OFFICIAL_WEBSITE_URL", officialWebsiteUrl.asBuildConfigString())
        buildConfigField("String", "RUNTIME_CONFIG_PUBLIC_KEY", runtimeConfigPublicKey.asBuildConfigString())
        buildConfigField("String", "CERT_PINS", certificatePins.asBuildConfigString())
    }

    signingConfigs {
        if (signingProperties != null) {
            create("release") {
                storeFile = file(requireNotNull(signingProperties["storeFile"]))
                storePassword = requireNotNull(signingProperties["storePassword"])
                keyAlias = requireNotNull(signingProperties["keyAlias"])
                keyPassword = requireNotNull(signingProperties["keyPassword"])
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // 有发布证书时 debug 也用它签名，两种包可以互相覆盖安装而不丢数据。
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { output ->
                output.versionName.set("v1.0.1")
                output.versionCode.set(913)
            }
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // core library desugaring 的运行时库（配合上面的 isCoreLibraryDesugaringEnabled）。
    // 2.1.5 是 2.x 线的稳定版，要求 AGP 7.4+，本项目 AGP 9.3.2 满足。
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation(project(":glassui"))
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("com.github.Dimezis:BlurView:version-2.0.4")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
