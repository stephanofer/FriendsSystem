# Instalación y Build

Este documento describe cómo se construye ProxySettings y qué artefacto debe usarse en Velocity.

## Requisitos del proyecto

ProxySettings compila con Java 25.

El motivo es práctico: `com.hera.craftkit:craftkit-database:1.1.0` requiere runtime Java 25 o superior. El build fallaba con Java 21 por incompatibilidad de bytecode.

## Dependencias principales

Declaradas en `gradle/libs.versions.toml`:

| Dependencia | Versión | Uso |
|---|---:|---|
| `com.velocitypowered:velocity-api` | `3.5.0-SNAPSHOT` | API Velocity y annotation processor. |
| `com.hera.craftkit:craftkit-database` | `1.1.0` | Pool MySQL, executor DB y utilidades de tabla/prefix. |
| `com.stephanofer.boostedyaml:boosted-yaml` | `1.3.7` | Carga y actualización de YAML. |
| `com.github.ben-manes.caffeine:caffeine` | `3.2.4` | Cache local de snapshots. |
| `net.kyori:adventure-api` | `4.25.0` | Componentes Adventure. |
| `net.kyori:adventure-text-minimessage` | `4.25.0` | Render de MiniMessage y placeholders. |
| `net.kyori:adventure-text-serializer-plain` | `4.25.0` | Disponible para consumidores si lo necesitan. |

## Comando de build verificado

El wrapper raíz de este proyecto está incompleto actualmente: no existe `gradlew.bat` en la raíz y no está `gradle-wrapper.jar` bajo `gradle/wrapper`.

El build fue verificado usando el wrapper vecino de `adventure` como ejecutor Gradle:

```powershell
& "C:\Users\vendi\Documents\software-workspace\java-projects\herav2\ProxySettings\adventure\gradlew.bat" -p "C:\Users\vendi\Documents\software-workspace\java-projects\herav2\ProxySettings" build
```

Resultado verificado:

```text
BUILD SUCCESSFUL
```

## JAR final para deploy

El JAR deployable es el `shadowJar`:

```text
target/ProxySettings-1.0.jar
```

También puede existir:

```text
build/libs/ProxySettings-1.0.jar
```

Ese segundo archivo viene del task normal `jar`. Para deploy en Velocity se debe usar el JAR de `target/`.

## Publicación en Maven Local

ProxySettings publica su artefacto Java para consumidores mediante `maven-publish`.

Coordenadas:

```kotlin
com.stephanofer:proxysettings:1.0
```

Comando:

```powershell
& "C:\Users\vendi\Documents\software-workspace\java-projects\herav2\ProxySettings\adventure\gradlew.bat" -p "C:\Users\vendi\Documents\software-workspace\java-projects\herav2\ProxySettings" publishToMavenLocal
```

Uso recomendado en un plugin consumidor:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.stephanofer:proxysettings:1.0")
}
```

`compileOnly` es lo correcto para consumidores Velocity porque ProxySettings debe estar instalado como plugin en el proxy. El consumidor compila contra la API, pero no debe sombrear ProxySettings dentro de su propio JAR.

La publicación usa el task normal `jar`, no el `shadowJar`. Esto es intencional: Maven Local entrega el artefacto de compilación para consumidores, mientras que `target/ProxySettings-1.0.jar` sigue siendo el JAR sombreado para deploy en Velocity.

## ShadowJar y relocations

El build usa `com.gradleup.shadow` y sigue el patrón de `NetworkPlayerSettings`.

Configuración relevante en `build.gradle.kts`:

```kotlin
shadowJar {
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("target"))
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("INFO_BIN", "INFO_SRC", "README")
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    relocate("com.hera.craftkit", "com.stephanofer.proxysettings.libs.craftkit")
    relocate("dev.dejvokep.boostedyaml", "com.stephanofer.proxysettings.libs.boostedyaml")
    relocate("com.zaxxer", "com.stephanofer.proxysettings.libs.zaxxer")
    relocate("com.github.benmanes.caffeine", "com.stephanofer.proxysettings.libs.caffeine")
}
```

Relocations activas:

| Original | Relocado |
|---|---|
| `com.hera.craftkit` | `com.stephanofer.proxysettings.libs.craftkit` |
| `dev.dejvokep.boostedyaml` | `com.stephanofer.proxysettings.libs.boostedyaml` |
| `com.zaxxer` | `com.stephanofer.proxysettings.libs.zaxxer` |
| `com.github.benmanes.caffeine` | `com.stephanofer.proxysettings.libs.caffeine` |

## Dependencias transitivas de database

`craftkit-database` trae transitivamente:

- `org.flywaydb:flyway-core`;
- `org.flywaydb:flyway-mysql`;
- `com.zaxxer:HikariCP`;
- `com.mysql:mysql-connector-j`.

En el JAR actual:

- `HikariCP` queda relocado por la regla `com.zaxxer`;
- `Flyway` queda sin relocation;
- `mysql-connector-j` queda sin relocation.

Esto está alineado con el patrón actual de `NetworkPlayerSettings`. ProxySettings no ejecuta migraciones, pero Flyway entra porque `craftkit-database` lo trae como dependencia transitiva.

## Descriptor Velocity

El descriptor generado por el annotation processor contiene:

```json
{
  "id": "proxysettings",
  "name": "ProxySettings",
  "version": "1.0",
  "description": "Velocity companion API for NetworkPlayerSettings",
  "main": "com.stephanofer.proxysettings.ProxySettingsPlugin"
}
```

El plugin id real para consumidores es:

```text
proxysettings
```
