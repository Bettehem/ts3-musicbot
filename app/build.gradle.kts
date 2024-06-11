import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.9.22"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jmailen.kotlinter") version "4.3.0"
    id("java")
}

group = "ts3-musicbot"
version = "master"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    implementation("org.json:json:20230227")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.3-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.6.3-native-mt")
    implementation("com.github.bettehem:ts3j:1.0.21")
    implementation("org.openjfx:javafx-controls:17")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}
// JavaFX modules to include
javafx {
    version = "17"
    modules = listOf("javafx.controls")
}

application {
    // Define the main class for the application.
    mainClass = "ts3musicbot.Main"
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "11"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "11"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("ts3-musicbot")
    archiveFileName.set("ts3-musicbot.jar")
    mergeServiceFiles()
    minimize()
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass))
    }
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }
    startScripts {
        dependsOn(shadowJar)
    }
    distTar {
        enabled = false
    }
    distZip {
        enabled = false
    }
    jar {
        enabled = false
    }
}
