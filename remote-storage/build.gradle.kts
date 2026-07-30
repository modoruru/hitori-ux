plugins {
    id("java-library")
    id("com.gradleup.shadow").version("9.0.0-beta4")
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:${property("websocket_version")}")
    implementation("org.json:json:20251224")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.xerial:sqlite-jdbc:3.53.2.0")
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
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
            artifactId = "remote-storage"
            group = "su.hitori.ux"
            version = rootProject.version.toString()

            artifact(tasks.named("shadowJar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}