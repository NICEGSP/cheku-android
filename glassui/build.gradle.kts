import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "com.cheku.glass.ui"
        compileSdk = 37
        // 与 :app 一致（Android 6.0）。
        // 本模块不使用 java.time / java.util.Base64，无需 core library desugaring。
        minSdk = 23
        buildToolsVersion = "37.0.0"
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                // KMP 的 KotlinDependencyHandler 没有 platform() 扩展点，
                // 必须用 project.dependencies.platform(...) 构造 BOM 约束对象。
                implementation(project.dependencies.platform("androidx.compose:compose-bom:2026.09.00"))
                implementation("androidx.compose.animation:animation-core")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-util")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.github.kyant0:backdrop:2.0.1")
                implementation("io.github.kyant0:shapes:1.2.1")
            }
        }

        androidMain {
            dependencies {
                // utils/Coroutines.kt 的 androidMain actual 直接委托
                // kotlinx.coroutines.android.awaitFrame，故需要该 artifact。
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            }
        }
    }
}
