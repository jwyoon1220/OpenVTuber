plugins {
    kotlin("jvm") version "2.4.0" apply false
}

group = "io.github.jwyoon1220"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

tasks.register("run") {
    group = "application"
    description = "Runs OpenVTuber from the run directory."
    dependsOn(":application:run")
}
