plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("com.gradleup.shadow") version "9.4.3"
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.modoruru:hitori:${property("hitori_version")}")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.0")

    implementation("org.java-websocket:Java-WebSocket:${property("websocket_version")}")
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName.set("ux")
        archiveClassifier.set("")

        relocate("org.java_websocket", "su.hitori.ux.shaded.websocket")

        dependencies {
            exclude(dependency("org.slf4j:slf4j-api:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("hitori.properties") {
            expand("version" to rootProject.version)
        }
    }
}

extensions.configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "module"

            artifact(tasks.named("shadowJar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}