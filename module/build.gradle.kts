plugins {
    java
    id("io.papermc.paperweight.userdev").version("2.0.0-beta.21")
    id("com.gradleup.shadow").version("9.4.3")
}

dependencies {
    paperweight.foliaDevBundle("26.2.build.+")
    compileOnly("dev.folia:folia-api:26.2.build.+")
    compileOnly("su.hitori:hitori:${property("hitori_version")}")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.0")

    implementation("org.java-websocket:Java-WebSocket:${property("websocket_version")}")
    implementation("com.h2database:h2:${property("h2_version")}")
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
    repositories {
        maven {
            name = "modoruReleases"
            url = uri("https://repository.modoru.fun/releases")

            credentials {
                username = System.getenv("MODORU_USERNAME") ?: ""
                password = System.getenv("MODORU_TOKEN") ?: ""
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = "module"
            group = "su.hitori.ux"
            version = rootProject.version.toString()

            artifact(tasks.named("shadowJar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}