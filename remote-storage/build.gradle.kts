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
    publications {
        create<MavenPublication>("maven") {
            artifactId = "remote-storage"

            artifact(tasks.named("shadowJar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}