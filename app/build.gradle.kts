import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun localProperty(key: String, defaultValue: String): String =
    localProperties.getProperty(key)?.trim().orEmpty().ifEmpty { defaultValue }

fun gradleProperty(key: String, defaultValue: String): String =
    providers.gradleProperty(key).orNull?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue

val apiPort = localProperty("api.port", gradleProperty("natsu.api.port", "8000"))
val apiHost = localProperty("api.host", gradleProperty("natsu.api.host", "127.0.0.1"))
val debugApiBaseUrl = localProperty(
    "api.base.url",
    "http://$apiHost:$apiPort/api/v1/",
)
val releaseApiBaseUrl = localProperty(
    "api.base.url.release",
    gradleProperty("natsu.api.base.url.release", "https://natsu.example.com/api/v1/"),
)

android {
    namespace = "io.mikoshift.natsu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.mikoshift.natsu"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$debugApiBaseUrl\"",
            )
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$releaseApiBaseUrl\"",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
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

val jacocoClassExcludes = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*\$Companion.class",
    "**/*Test*.*",
    "android/**/*.*",
    "**/databinding/**",
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generate JaCoCo coverage report for debug unit tests"
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport"))
    }

    // AGP 9+ compiles Kotlin to built_in_kotlinc; older AGP used tmp/kotlin-classes.
    val classDirs = listOf(
        layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
        layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        layout.buildDirectory.dir("intermediates/javac/debug/classes"),
    ).map { dir ->
        fileTree(dir) { exclude(jacocoClassExcludes) }
    }

    classDirectories.setFrom(files(classDirs))
    sourceDirectories.setFrom(
        files("src/main/java", "src/main/kotlin"),
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/testDebugUnitTest.exec",
            )
        },
    )
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
    implementation(libs.androidx.work.runtime.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20260522")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val adbExecutable = localProperty("sdk.dir", "")
    .let { sdkDir -> if (sdkDir.isEmpty()) "adb" else "$sdkDir/platform-tools/adb" }

tasks.register<Exec>("adbReverseApi") {
    group = "android"
    description = "Forward host API port to emulator localhost (debug)"
    commandLine(adbExecutable, "reverse", "tcp:$apiPort", "tcp:$apiPort")
    isIgnoreExitValue = true
}

tasks.matching { it.name == "installDebug" }.configureEach {
    dependsOn("adbReverseApi")
}
