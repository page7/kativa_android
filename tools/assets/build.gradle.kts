plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.kativa.tools.assets.GenerateAssetsKt")
}

dependencies {
    implementation("com.github.weisj:jsvg:2.2.0")
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
