plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kativa.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kativa.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        @Suppress("UnstableApiUsage")
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Regenerates every PNG in app/src/main/res from assets/manifest.json (+ assigns/*.svg).
// See docs/ASSETS.md.
tasks.register<JavaExec>("generateAssets") {
    group = "kativa"
    description = "Render placeholder/provided SVGs from assets/manifest.json into Android res/ PNGs."
    val repoRoot = rootProject.layout.projectDirectory.asFile
    workingDir = repoRoot
    classpath = project(":tools:assets").extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("com.kativa.tools.assets.GenerateAssetsKt")
    args(repoRoot.absolutePath)
    dependsOn(":tools:assets:classes")
}

tasks.register("checkAssetsRegistered") {
    group = "verification"
    description = "Fails when a PNG under app/src/main/res is not declared in assets/manifest.json."
    val resDir = layout.projectDirectory.dir("src/main/res").asFile
    val manifestFile = rootProject.layout.projectDirectory.file("assets/manifest.json").asFile
    doLast {
        val ids = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(manifestFile.readText())
            .map { it.groupValues[1] }.toSet()
        val orphans = resDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "png" }
            .filter { f -> f.nameWithoutExtension.removeSuffix("_foreground") !in ids &&
                f.nameWithoutExtension !in ids &&
                f.nameWithoutExtension !in setOf("ic_launcher", "ic_launcher_round") }
            .map { it.relativeTo(resDir).path }
            .toList()
        if (orphans.isNotEmpty()) {
            throw GradleException("PNGs not registered in assets/manifest.json:\n" + orphans.joinToString("\n"))
        }
    }
}
