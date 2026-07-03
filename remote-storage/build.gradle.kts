plugins {
    id("java-library")
}

dependencies {
    api("org.java-websocket:Java-WebSocket:${properties["websocket_version"]}")
    api("org.json:json:20251224")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.xerial:sqlite-jdbc:3.53.2.0")
}

extensions.configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "remote-storage"

            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}