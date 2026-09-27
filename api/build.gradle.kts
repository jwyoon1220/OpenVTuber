plugins {
    kotlin("jvm")
    `java-library`
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val logbackVersion = providers.gradleProperty("logbackVersion").get()
val fastutilVersion = providers.gradleProperty("fastutilVersion").get()
val jctoolsVersion = providers.gradleProperty("jctoolsVersion").get()

dependencies {
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("it.unimi.dsi:fastutil:$fastutilVersion")
    implementation("org.jctools:jctools-core:$jctoolsVersion")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
