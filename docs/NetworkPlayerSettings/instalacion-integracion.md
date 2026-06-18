# Instalación e integración de un plugin consumidor

Este documento explica cómo declarar y obtener NetworkPlayerSettings desde otro plugin Paper/Bukkit usando únicamente comportamiento visible en el repositorio.

## Dependencias reales del proyecto

El proyecto se compila como `java-library`, genera un JAR sombreado con Shadow y expone publicación local con `maven-publish`. El `build.gradle.kts` declara:

| Dependencia | Uso visible |
|---|---|
| `io.papermc.paper:paper-api:26.1.2.build.69-stable` | API Paper de compilación. |
| `me.clip:placeholderapi:2.12.2` | Integración opcional PlaceholderAPI. |
| `com.hera.craftkit:craftkit-database:1.1.0` | Base de datos y migraciones internas. |
| `com.hera.craftkit:craftkit-zmenu:1.1.0` | Bootstrap de zMenu interno. |
| `com.stephanofer.boostedyaml:boosted-yaml:1.3.7` | Carga y auto-update de YAML internos. |
| `org.incendo:cloud-paper:2.0.0-beta.15` y `cloud-minecraft-extras` | Comando `/globalsettings`. |
| `com.maxmind.geoip2:geoip2:5.1.0` | Resolución GeoIP interna. |

## Publicación local de NetworkPlayerSettings

El proyecto define estas coordenadas Maven locales:

```kotlin
group = "com.stephanofer"
version = "1.0.0-SNAPSHOT"
artifactId = "networkplayersettings"
```

Para instalar el artefacto en Maven Local desde este proyecto:

```bash
./gradlew publishToMavenLocal
```

En Windows también podés usar:

```powershell
.\gradlew.bat publishToMavenLocal
```

Después, un plugin consumidor puede compilar contra la API pública así:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.stephanofer:networkplayersettings:1.0.0-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
}
```

Usá `compileOnly`, no `implementation`, porque NetworkPlayerSettings debe estar instalado como plugin en el servidor. El consumidor necesita las clases para compilar, pero no debe sombrear ni empaquetar NetworkPlayerSettings dentro de su propio JAR.

### Cuándo usar `libs/`

`libs/NetworkPlayerSettings.jar` sigue siendo posible para pruebas puntuales, pero no es la opción recomendada para desarrollo normal de varios plugins consumidores. Maven Local es más limpio porque versiona la dependencia, evita copias manuales y hace explícito qué versión consume cada plugin.

Ejemplo válido pero menos recomendable:

```kotlin
dependencies {
    compileOnly(files("libs/NetworkPlayerSettings.jar"))
}
```

### Límite actual

La publicación configurada es local. El proyecto no declara un repositorio Maven remoto para publicar releases compartidos. Si varios desarrolladores o un CI necesitan resolver la dependencia sin depender de la máquina local, publicá el mismo artefacto en un Maven privado y reemplazá `mavenLocal()` por ese repositorio.

## Metadata del plugin consumidor

`NetworkPlayerSettings` se registra como plugin con este nombre exacto:

```yaml
name: NetworkPlayerSettings
main: com.stephanofer.networkplayersettings.NetworkPlayerSettingsPlugin
depend: [zMenu]
softdepend: [PlaceholderAPI]
```

Si tu plugin necesita NetworkPlayerSettings para funcionar, declaralo como dependencia fuerte:

```yaml
depend: [NetworkPlayerSettings]
```

Si tu plugin puede funcionar sin esta integración, usá dependencia blanda y tratá el servicio ausente:

```yaml
softdepend: [NetworkPlayerSettings]
```

## Lookup de servicios

NetworkPlayerSettings registra dos servicios en `ServicesManager`:

| Servicio | Registro real | Cuándo aparece |
|---|---|---|
| `PlayerSettingsService` | `register(PlayerSettingsService.class, settingsService, plugin, ServicePriority.Normal)` | Después de inicializar config, assets, DB, zMenu, servicio, placeholders, comandos y listeners. |
| `NetworkAssetService` | `register(NetworkAssetService.class, service, plugin, ServicePriority.Normal)` | Durante `initializeAssets()`, antes del servicio de ajustes. |

Ejemplo recomendado en `onEnable()` de un consumidor con `depend`:

```java
private PlayerSettingsService playerSettings;

@Override
public void onEnable() {
    this.playerSettings = getServer().getServicesManager().load(PlayerSettingsService.class);
    if (this.playerSettings == null) {
        getLogger().severe("NetworkPlayerSettings is enabled but PlayerSettingsService is not registered.");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
}
```

Ejemplo opcional con `softdepend`:

```java
Optional<PlayerSettingsService> settings = Optional.ofNullable(
    Bukkit.getServicesManager().load(PlayerSettingsService.class)
);
```

## Lifecycle y orden correcto

NetworkPlayerSettings hace en `onEnable()`:

1. carga `config.yml` con `PluginYamlLoader`;
2. carga `assets/countries.yml` y registra `NetworkAssetService`;
3. abre base de datos MySQL y ejecuta migraciones;
4. requiere zMenu y carga inventarios/dialogs;
5. crea `DefaultPlayerSettingsService`;
6. registra PlaceholderAPI si aplica;
7. registra comando, listener y `PlayerSettingsService`.

En `onDisable()` desregistra la expansión PlaceholderAPI, desregistra todos sus servicios y cierra GeoIP/DB.

Para consumidores, esto significa:

- Con `depend: [NetworkPlayerSettings]`, buscá servicios en tu `onEnable()` o más tarde.
- Con `softdepend`, el servicio puede ser `null`; tu plugin debe degradar funcionalidad.
- No guardes referencias indefinidamente si soportás reloads externos. Si `NetworkPlayerSettings` se deshabilita, sus servicios se desregistran.

## Dependencias transitivas de runtime del servidor

NetworkPlayerSettings declara `depend: [zMenu]`; si zMenu no está instalado, Paper/Bukkit no debería habilitar NetworkPlayerSettings. PlaceholderAPI es opcional por metadata y por config.

Un plugin consumidor no necesita depender directamente de zMenu salvo que también use zMenu por cuenta propia. Para usar placeholders `%playersettings_*%`, el servidor debe tener PlaceholderAPI y `placeholderapi.enabled: true`.
