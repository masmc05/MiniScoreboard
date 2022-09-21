plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("xyz.jpenilla.run-paper") version "1.0.6"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
}

group = "me.masmc05.minisb"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.2")
    library("cloud.commandframework:cloud-paper:1.7.1")
    library("cloud.commandframework:cloud-annotations:1.7.1")
    implementation(projects.nms.base)
    implementation(project(path = ":nms:v1_18_2", configuration = "reobf"))
    implementation(project(path = ":nms:v1_19_2", configuration = "reobf"))
}

java {
    charset("UTF-8")
}

tasks {
    runServer {
        this.minecraftVersion.set("1.18.2")
    }
    compileJava {
        this.options.encoding = "UTF-8"
    }
}
// Configure plugin.yml generation
bukkit {
    main = "me.masmc05.minisb.MiniScoreBoard"
    apiVersion = "1.18"
    authors = listOf("masmc05")
    version = rootProject.version.toString()
    prefix = "MiniSB"
    name = rootProject.name
    softDepend = listOf("PlaceholderAPI")
}
