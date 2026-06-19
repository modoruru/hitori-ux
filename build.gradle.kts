plugins {
    java
    id("io.papermc.paperweight.userdev").version("2.0.0-beta.17").apply(false)
}

subprojects {
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        maven("https://jitpack.io") {
            name = "jitpack"
        }
        maven("https://repo.codemc.org/repository/maven-public/") {
            name = "codemc"
        }
    }

    tasks {
        val sourcesJar by registering(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.main.get().allSource)
        }
    }
}

tasks.jar {
    enabled = false
}