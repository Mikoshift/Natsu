plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.mikoshift.natsu"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.mikoshift.natsu"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/CONTRIBUTORS.md",
                "/META-INF/LICENSE.md",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
            )
        }
    }
}

val compileReaderJs = tasks.register<Exec>("compileReaderJs") {
    group = "build"
    description = "Bundle reader WebView JavaScript assets"
    workingDir = rootProject.projectDir
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    commandLine(
        if (isWindows) {
            listOf("cmd", "/c", "npm install && npm run reader:build")
        } else {
            listOf("sh", "-c", "npm install && npm run reader:build")
        },
    )
    inputs.dir(rootProject.file("reader/src"))
    inputs.files(
        rootProject.file("package.json"),
        rootProject.file("package-lock.json"),
        rootProject.file("reader/esbuild.config.mjs"),
        rootProject.file("reader/tsconfig.json"),
        rootProject.file("reader/theme.css"),
    )
    outputs.files(
        file("src/main/assets/reader/bridge.js"),
        file("src/main/assets/reader/theme.css"),
    )
}

tasks.named("preBuild").configure {
    dependsOn(compileReaderJs)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.commonmark)
    implementation(libs.coil.compose)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.jsoup)
    implementation(libs.androidx.webkit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
