plugins {

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