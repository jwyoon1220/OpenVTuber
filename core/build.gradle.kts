plugins {
    kotlin("jvm")
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val logbackVersion = providers.gradleProperty("logbackVersion").get()
val fastutilVersion = providers.gradleProperty("fastutilVersion").get()
val jctoolsVersion = providers.gradleProperty("jctoolsVersion").get()
val lwjglVersion = providers.gradleProperty("lwjglVersion").get()
val jomlVersion = providers.gradleProperty("jomlVersion").get()
val koolVersion = providers.gradleProperty("koolVersion").get()

dependencies {
    implementation(project(":api"))

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("it.unimi.dsi:fastutil:$fastutilVersion")
    implementation("org.jctools:jctools-core:$jctoolsVersion")

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-vulkan")
    implementation("org.lwjgl:lwjgl-openxr")
    implementation("org.lwjgl:lwjgl-openvr")
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-openxr::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-openvr::natives-windows")
    implementation("org.lwjgl:lwjgl-assimp")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-jemalloc::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-nfd::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-shaderc::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-vma::natives-windows")

    implementation("org.joml:joml:$jomlVersion")
    implementation("de.fabmax.kool:kool-core-desktop:$koolVersion")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("convertModel") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.jwyoon1220.core.asset.FbxConverter"
    args(
        rootProject.layout.projectDirectory.file("run/model/fbx/Neru.fbx").asFile.absolutePath,
        rootProject.layout.projectDirectory.file("run/model/cache/Neru.glb").asFile.absolutePath,
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
