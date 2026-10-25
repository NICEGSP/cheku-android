buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    dependencies {
        // 目标 Kotlin 版本：2.4.10
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        // KSP 自 2.3.0 起使用独立版本号，不再与 Kotlin 版本绑定
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.2" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
