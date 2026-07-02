plugins {
    id("java-library")
}

dependencies {
    api("org.java-websocket:Java-WebSocket:${properties["websocket_version"]}")
    api("org.json:json:20251224")

    testImplementation("org.xerial:sqlite-jdbc:3.53.2.0")
}

extensions.configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "hitori-ux_remote-storage"

            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}