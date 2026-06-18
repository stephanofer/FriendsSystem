# Configuración de NetworkPlayerSettings

La configuración se carga con `PluginYamlLoader`, que usa BoostedYAML con auto-update y versionado por `file-version`. Los archivos bundled se copian/actualizan en la carpeta de datos del plugin.

## `config.yml`

Recurso fuente: `src/main/resources/config.yml`.

```yaml
file-version: 1

database:
  host: "127.0.0.1"
  port: 3306
  database: "hera_network"
  username: "root"
  password: ""
  table-prefix: "nps_"
  pool:
    maximum-pool-size: 5
    minimum-idle: 1
  migrations:
    enabled: true

settings:
  default-language: "en"
  detect-client-locale: true
  cache-cleanup-on-quit: true
  language-change-cooldown-millis: 750

geoip:
  enabled: true
  database-path: "GeoLite2-Country.mmdb"

command:
  name: "globalsettings"
  aliases:
    - "settings"
    - "prefs"
  open:
    type: "menu"
    key: "language"

placeholderapi:
  enabled: true
  cache-ttl-millis: 250
```

## Defaults y comportamiento por sección

### `database`

| Key | Tipo | Default en código | Efecto |
|---|---:|---|---|
| `database.host` | String | `127.0.0.1` | Host MySQL. |
| `database.port` | int | `3306` | Puerto MySQL. |
| `database.database` | String | `hera_network` | Base de datos. |
| `database.username` | String | `root` | Usuario. |
| `database.password` | String | `""` | Password. |
| `database.table-prefix` | String | `nps_` | Prefijo aplicado por CraftKit a tablas, por ejemplo `nps_player_settings`. |
| `database.pool.maximum-pool-size` | int | `5` | Tamaño máximo del pool. No hay validación propia visible en `PluginConfig`. |
| `database.pool.minimum-idle` | int | `1` | Conexiones idle mínimas. No hay validación propia visible en `PluginConfig`. |
| `database.migrations.enabled` | boolean | `true` | Activa migraciones con `ExistingSchemaStrategy.BASELINE_AT_ZERO`; si es `false`, se construye `MigrationConfig` deshabilitado. |

Consecuencias para consumidores:

- Si DB o migraciones fallan durante startup, `NetworkPlayerSettingsPlugin#onEnable` captura el error, cierra recursos y deshabilita el plugin. Tus servicios no estarán disponibles.
- No escribas directamente en la tabla; usá `PlayerSettingsService`.
- Cambiar `table-prefix` cambia dónde se leen/escriben los ajustes.

### `settings`

| Key | Tipo | Default en código | Efecto |
|---|---:|---|---|
| `settings.default-language` | String | `en` | Se convierte con `Language.fromCode`; solo `es` produce `SPANISH`, todo lo demás produce `ENGLISH`. |
| `settings.detect-client-locale` | boolean | `true` | Si está activo, `AUTO` usa el locale del cliente (`es*`/`en*`). Si está apagado, `AUTO` cae en `default-language`. |
| `settings.cache-cleanup-on-quit` | boolean | `true` | Si está activo, al salir el jugador se elimina su snapshot cacheado. Si está apagado, queda cacheado pero `ready=false`. |
| `settings.language-change-cooldown-millis` | long | `750` | Cooldown del botón interno de idioma. Valores negativos se tratan como `0` al usarse. |

Impacto para consumidores:

- `resolvedLanguage(Player)` puede cambiar cuando el cliente cambia locale si la preferencia es `AUTO`.
- Con `cache-cleanup-on-quit: false`, `cached(UUID)` puede seguir devolviendo snapshot de un jugador desconectado, pero `isReady(UUID)` será `false`.

### `geoip`

| Key | Tipo | Default en código | Efecto |
|---|---:|---|---|
| `geoip.enabled` | boolean | `true` | Si es `false`, no se abre DB GeoIP y el país detectado depende de lo persistido/default. |
| `geoip.database-path` | String | `GeoLite2-Country.mmdb` | Si está vacío o `null`, se normaliza a `GeoLite2-Country.mmdb`. Ruta relativa apunta al data folder del plugin; ruta absoluta se usa tal cual. |

Comportamiento operativo:

- Si el archivo no existe, GeoIP se deshabilita con warning y el plugin sigue funcionando.
- Direcciones no públicas, loopback, link-local, site-local o multicast devuelven `XX`.
- Errores de lookup se loguean como warning y devuelven `XX`.
- Una detección desconocida no pisa un país real ya persistido.

### `command`

| Key | Tipo | Default en código | Efecto |
|---|---:|---|---|
| `command.name` | String | `globalsettings` | Comando principal. Si es blanco, fallback `globalsettings`. |
| `command.aliases` | List<String> | `settings`, `prefs` | Aliases trimmeados; blancos se eliminan; alias igual al nombre principal se elimina. |
| `command.open.type` | String | `menu` | `dialog` produce `DIALOG`; cualquier otro valor produce `MENU`. |
| `command.open.key` | String | `language` | Key de inventario/dialog a abrir; si es blanco, fallback `language`. |

El comando solo acepta jugadores (`senderType(PlayerSource.class)`). Si el jugador no está ready, envía mensaje `settings.loading`.

### `placeholderapi`

| Key | Tipo | Default en código | Efecto |
|---|---:|---|---|
| `placeholderapi.enabled` | boolean | `true` | Si es `false`, no registra expansión. |
| `placeholderapi.cache-ttl-millis` | long | `250` | TTL de cache interno de placeholders. Valores negativos se convierten en `Duration` negativa y no cachean porque el código solo cachea si no es cero ni negativo. |

Si PlaceholderAPI no está instalado pero la config está activa, NetworkPlayerSettings loguea warning y sigue sin expansión.

## `assets/countries.yml`

Recurso fuente: `src/main/resources/assets/countries.yml`.

```yaml
file-version: 1

countries:
  XX:
    name: Unknown
    head-texture-base64: "eyJ0ZXh0dXJlcyI6e319"
    aliases: [unknown]
  AR:
    name: Argentina
    head-texture-base64: "eyJ0ZXh0dXJlcyI6e319"
    aliases: [argentina, south-america]
```

Reglas reales del loader:

- Debe existir sección `countries` con entries.
- Cada key debe ser código ISO alpha-2 normalizable a mayúsculas.
- Debe existir el asset fallback `XX`.
- `name` no puede estar en blanco.
- `head-texture-base64` no puede estar en blanco y debe decodificar como Base64.
- `aliases` se normalizan a minúsculas.
- No puede haber códigos duplicados tras normalización.
- No puede haber aliases duplicados.
- Un alias no puede colisionar con un código canónico.

Si el catálogo es inválido, el bootstrap de assets falla y el plugin no registra `NetworkAssetService`.

## `inventories/language.yml`

Este recurso configura el menú interno zMenu de idioma. No es una API pública, pero afecta la UX del comando.

- Inventario `language`, tamaño `27`.
- Botones `NPS_LANGUAGE` para `es`, `auto`, `en` en slots `11`, `13`, `15`.
- El loader `LanguageButtonLoader` lee `language`; valores desconocidos caen en `LanguagePreference.AUTO`.

Placeholders internos del botón:

| Placeholder | Valor |
|---|---|
| `%language_option%` | `auto`, `es` o `en`. |
| `%language_selected%` | `true`/`false`. |
| `%selected_marker%` | `✔ ` si está seleccionado; vacío si no. |
| `%selected_state%` | Mensaje localizado de seleccionado/disponible. |
| `%effective_language%` | Código del idioma efectivo actual. |
| `%current_preference%` | Preferencia actual guardada/cacheada. |

## `messages/*.properties`

Mensajes internos disponibles para inglés/español:

- `settings.loading`
- `settings.menu-open-failed`
- `settings.language.updated`
- `settings.language.already-selected`
- `settings.language.cooldown`
- `settings.language.auto-name`
- `menu.language.selected-state`
- `menu.language.available-state`

No hay API pública para que plugins consumidores registren mensajes en `PluginMessages`.

## Migraciones DB incluidas

- `V1__create_player_settings.sql`: crea tabla `${tablePrefix}player_settings`.
- `V2__remove_detected_locale_setting.sql`: elimina filas antiguas con `setting_key = 'detected_locale'`.

Si `database.migrations.enabled` está apagado, el proyecto no crea/actualiza el schema por sí mismo.
