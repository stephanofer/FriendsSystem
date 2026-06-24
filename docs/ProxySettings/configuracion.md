# Configuración

ProxySettings genera y lee `config.yml` desde el directorio de datos del plugin en Velocity.

El loader real está en:

```text
src/main/java/com/stephanofer/proxysettings/config/ProxySettingsYamlLoader.java
```

## Archivo default

```yaml
config-version: 1

database:
  host: "127.0.0.1"
  port: 3306
  database: "hera_network"
  username: "root"
  password: ""
  table-prefix: "nps_"
  pool:
    maximum-pool-size: 6
    minimum-idle: 1

settings:
  default-language: "es"
  detect-client-locale: true
  cache-cleanup-on-disconnect: true
  cache-maximum-size: 50000
```

## `database`

ProxySettings usa `craftkit-database` para crear un pool MySQL. No ejecuta migraciones.

| Key | Default | Uso real |
|---|---:|---|
| `database.host` | `127.0.0.1` | Host MySQL. |
| `database.port` | `3306` | Puerto MySQL. |
| `database.database` | `hera_network` | Base de datos donde vive la tabla de NetworkPlayerSettings. |
| `database.username` | `root` | Usuario MySQL. |
| `database.password` | `""` | Password MySQL. |
| `database.table-prefix` | `nps_` | Prefijo usado para resolver la tabla `${prefix}player_settings`. |
| `database.pool.maximum-pool-size` | `6` | Tamaño máximo del pool Hikari. Se fuerza mínimo `1`. |
| `database.pool.minimum-idle` | `1` | Conexiones idle mínimas. Se limita entre `0` y `maximum-pool-size`. |

## Tabla leída

El repositorio SQL usa:

```java
database.table("player_settings")
```

Con el default `table-prefix: "nps_"`, la tabla efectiva es:

```text
nps_player_settings
```

La tabla debe existir antes de iniciar ProxySettings. La crea y administra `NetworkPlayerSettings` del lado Paper.

## `settings`

| Key | Default | Uso real |
|---|---:|---|
| `settings.default-language` | `es` | Idioma fallback. Solo `es` produce `SPANISH`; cualquier otro valor cae en `ENGLISH`. |
| `settings.detect-client-locale` | `true` | Si está activo, `AUTO` usa el locale del cliente Velocity. Si está apagado, `AUTO` usa `default-language`. |
| `settings.cache-cleanup-on-disconnect` | `true` | Si está activo, se invalida el snapshot al desconectar. |
| `settings.cache-maximum-size` | `50000` | Tamaño máximo de la cache Caffeine. Se fuerza mínimo `1`. |

## Configuración de migraciones

No existe configuración de migraciones en ProxySettings.

El código construye internamente:

```java
MigrationConfig.builder()
    .enabled(false)
    .classLoader(classLoader)
    .build();
```

Esto es intencional. ProxySettings no es dueño del schema de `NetworkPlayerSettings`.

## Auto-update YAML

El loader usa BoostedYAML con:

- `LoaderSettings.setAutoUpdate(true)`;
- `UpdaterSettings` con versionado por `config-version`;
- separador de rutas `.`.

Esto aplica a:

- `config.yml`;
- `assets/countries.yml`;
- `styles/nick-patterns.yml`;
- `styles/chat-patterns.yml`.
