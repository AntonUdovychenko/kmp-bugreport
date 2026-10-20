import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    android {
        namespace = "io.github.antonudovychenko.bugreport.ktor"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()
    js {
        browser { testTask { enabled = false } }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":bugreport"))
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(artifactId = "bugreport-ktor")
    pom {
        name.set("KMP Bug Report — Ktor sender")
        description.set("Sends KMP Bug Report reports as multipart/form-data with a Ktor HttpClient.")
    }
}
