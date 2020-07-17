import org.jetbrains.kotlin.utils.addToStdlib.cast

repositories {
    jcenter()
}

plugins {
    application
    kotlin("jvm") version "1.3.72"
}

application {
    mainClass.set("HelloKt")
}

tasks {
    withType<JavaExec> {
        args = project.property("patcher.args").cast<String>().split(' ')
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.javassist:javassist:3.27.0-GA")
    implementation("net.lingala.zip4j:zip4j:2.6.1")
}
