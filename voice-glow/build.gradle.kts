import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        publishLibraryVariants("release")
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    iosArm64()
    iosSimulatorArm64()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "io.github.dwite.voiceglow"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The coordinates are GROUP and VERSION_NAME in the root gradle.properties and
// POM_ARTIFACT_ID in this module's. `./gradlew publishToMavenLocal` works as
// is; Maven Central needs the credentials described in the README.
mavenPublishing {
    publishToMavenCentral()
    // Signed when a key is configured; a local publish needs none.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    pom {
        name.set("voice-glow")
        description.set("A sound-reactive glow for Compose Multiplatform: a port of Jakub Antalik's voice-glow.")
        url.set("https://github.com/Dwite/voice-glow-compose")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("Dwite")
                name.set("Valerii Kuznietsov")
                url.set("https://github.com/Dwite")
            }
        }
        scm {
            url.set("https://github.com/Dwite/voice-glow-compose")
            connection.set("scm:git:git://github.com/Dwite/voice-glow-compose.git")
            developerConnection.set("scm:git:ssh://git@github.com/Dwite/voice-glow-compose.git")
        }
    }
}
