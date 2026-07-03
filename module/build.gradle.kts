plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("com.gradleup.shadow") version "9.4.3"
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-paper-core:11.0.0")
    compileOnly("com.github.modoruru:hitori:${properties.getOrDefault("hitori_version", "")}")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.0")

    implementation("org.java-websocket:Java-WebSocket:${properties["websocket_version"]}")
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName.set("ux")
        archiveClassifier.set("")

        relocate("org.java_websocket", "su.hitori.ux.shaded.websocket")
        relocate("org.slf4j", "su.hitori.ux.shaded.slf4j")
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