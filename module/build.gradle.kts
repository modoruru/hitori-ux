plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-paper-core:11.0.0")
    compileOnly("com.github.modoruru:hitori:${properties.getOrDefault("hitori_version", "")}")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.0")
}

tasks {
    jar {
        archiveBaseName.set("ux")
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
            artifactId = "hitori-ux"

            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}