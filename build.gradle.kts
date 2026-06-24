import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    id("java-library")
    id("eclipse")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.stephanofer:proxysettings:1.0")

    implementation("com.stephanofer.boostedyaml:boosted-yaml:1.3.7")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.hera.craftkit:craftkit-database:1.1.0")
    implementation("com.hera.craftkit:craftkit-redis:1.1.0")
    implementation("org.incendo:cloud-velocity:2.0.0-beta.15")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")

    testImplementation("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    destinationDirectory.set(layout.projectDirectory.dir("target"))
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("INFO_BIN", "INFO_SRC", "README")

    dependencies {
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }

    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    relocate("com.hera.craftkit", "com.stephanofer.friendssystem.libs.craftkit")
    relocate("dev.dejvokep.boostedyaml", "com.stephanofer.friendssystem.libs.boostedyaml")
    relocate("org.incendo.cloud", "com.stephanofer.friendssystem.libs.cloud")
    relocate("io.leangen.geantyref", "com.stephanofer.friendssystem.libs.geantyref")
    relocate("com.github.benmanes.caffeine", "com.stephanofer.friendssystem.libs.caffeine")
    relocate("com.google.gson", "com.stephanofer.friendssystem.libs.gson")
    relocate("com.zaxxer", "com.stephanofer.friendssystem.libs.zaxxer")

    relocate("io.lettuce", "com.stephanofer.friendssystem.libs.lettuce")
    relocate("redis.clients.authentication", "com.stephanofer.friendssystem.libs.redis_authx")
    relocate("reactor", "com.stephanofer.friendssystem.libs.reactor")
    relocate("org.reactivestreams", "com.stephanofer.friendssystem.libs.reactivestreams")
    relocate("io.netty", "com.stephanofer.friendssystem.libs.netty")
}

tasks.jar {
    destinationDirectory.set(layout.projectDirectory.dir("target"))
}

tasks.test {
    useJUnitPlatform()
}


val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.main.configure { java.srcDir(generateTemplates.map { it.outputs }) }

project.idea.project.settings.taskTriggers.afterSync(generateTemplates)
project.eclipse.synchronizationTasks(generateTemplates)
