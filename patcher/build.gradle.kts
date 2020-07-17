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

dependencies {
    implementation(kotlin("stdlib"))
}
