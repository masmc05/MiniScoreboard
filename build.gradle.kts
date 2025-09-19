plugins {
    id("java")
    id("com.gradleup.shadow") version "9.1.0"
    id("xyz.jpenilla.run-paper") version "3.0.0"
    id("de.eldoria.plugin-yml.bukkit") version "0.8.0"
}

group = "me.masmc05.minisb"
version = "1.2"
java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 17 on systems that only have JDK 8 installed for example.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    library("org.incendo:cloud-paper:2.0.0-beta.10")
    library("org.incendo:cloud-annotations:2.0.0")
    implementation(projects.nms.base)
    implementation(projects.nms.v1218)
}

java {
    charset("UTF-8")
}

tasks {
    runServer {
        this.minecraftVersion("1.21.8")
    }
    compileJava {
        this.options.encoding = "UTF-8"
    }
    shadowJar {
        manifest {
            attributes(
                "paperweight-mappings-namespace" to "mojang"
            )
        }
    }
}
// Configure plugin.yml generation
bukkit {
    main = "me.masmc05.minisb.MiniScoreBoard"
    apiVersion = "1.21.8"
    authors = listOf("masmc05")
    version = rootProject.version.toString()
    prefix = "MiniSB"
    name = rootProject.name
    softDepend = listOf("PlaceholderAPI")
}
