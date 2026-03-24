plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("maven-publish")
}

val defaultJavaVersion = 23

repositories {
    mavenCentral()

    // papermc
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }

    // hitori
    maven("https://jitpack.io") {
        name = "jitpack"
    }

    // skinsrestorer
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc"
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-paper-core:11.0.0")

    compileOnly("com.github.modoruru:hitori:${properties.getOrDefault("hitori_version", "")}")

    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.0")
}

tasks {
    val sourcesJar by registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    processResources {
        filesMatching("hitori.properties") {
            expand("version" to rootProject.version)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = rootProject.name
            version = rootProject.version.toString()

            artifact(tasks.named("jar"))

            artifact(tasks.named("sourcesJar"))
        }
    }
}