plugins {
    id("java-library")
}

dependencies {
    api("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("org.json:json:20251224")
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