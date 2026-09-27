plugins {
    kotlin("jvm")
    application
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val logbackVersion = providers.gradleProperty("logbackVersion").get()
val fastutilVersion = providers.gradleProperty("fastutilVersion").get()
val jctoolsVersion = providers.gradleProperty("jctoolsVersion").get()

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("it.unimi.dsi:fastutil:$fastutilVersion")
    implementation("org.jctools:jctools-core:$jctoolsVersion")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "io.github.jwyoon1220.application.MainKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.layout.projectDirectory.dir("run").asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
}
