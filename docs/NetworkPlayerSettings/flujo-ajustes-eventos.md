# Flujo de ajustes, caché, persistencia y eventos

Este documento describe el comportamiento runtime visible en `DefaultPlayerSettingsService` y `PlayerConnectionListener`.

## Flujo de conexión

```text
AsyncPlayerConnectionConfigureEvent
  -> preloadForConnection(UUID, InetAddress)
      -> repository.loadOrCreate(UUID)
      -> si GeoIP está habilitado: resolveCountry(address)
      -> puede persistir detected_country sincronamente dentro del flujo async de conexión
      -> actualiza caché y marca ready=false

AsyncPlayerPreLoginEvent
  -> si no hay caché: preloadForLogin(UUID)
      -> repository.loadOrCreate(UUID)
      -> actualiza caché y marca ready=false

PlayerJoinEvent
  -> handleJoin(Player)
      -> getCachedOrDefault(UUID)
      -> recuerda locale actual si aplica
      -> resuelve idioma
      -> marca ready=true
      -> dispara PlayerSettingsReadyEvent

PlayerQuitEvent
  -> limpia cooldown de botón de idioma
  -> evict(UUID, config.settings.cache-cleanup-on-quit)
```

## Qué significa “ready”

`PlayerSettingsService#isReady(UUID)` usa un set interno `readyPlayers`.

- `false` durante prelogin/configuración y antes de `PlayerJoinEvent`.
- `true` después de `PlayerSettingsReadyEvent` en join.
- `false` al salir el jugador.

Para un plugin consumidor, el camino seguro es escuchar `PlayerSettingsReadyEvent` o comprobar `isReady` antes de abrir menús, renderizar UI sensible al idioma o ejecutar lógica que dependa de datos persistidos.

## Caché

`DefaultPlayerSettingsService` mantiene:

| Caché | Contenido | Limpieza |
|---|---|---|
| `cache` | `UUID -> PlayerSettingsSnapshot` | En quit solo si `settings.cache-cleanup-on-quit: true`. |
| `localeCache` | locale normalizado del jugador | Siempre se limpia en quit. |
| `readyPlayers` | UUIDs listos | Siempre se limpia en quit. |

`getCachedOrDefault(UUID)` no fuerza DB; si no hay caché devuelve defaults. Si necesitás cargar desde DB cuando no hay caché, usá `load(UUID)` y encadená el future.

## Idioma efectivo

La preferencia persistida puede ser:

| Preferencia | Valor persistido | Resolución |
|---|---|---|
| `AUTO` | `auto` | Usa locale del cliente si `settings.detect-client-locale` está activo. Si no se reconoce, usa `settings.default-language`. |
| `SPANISH` | `es` | Siempre `Language.SPANISH`. |
| `ENGLISH` | `en` | Siempre `Language.ENGLISH`. |

Locales `es`, `es_*`, `en`, `en_*` se reconocen. Otros locales caen en el idioma default configurado.

Si `settings.detect-client-locale: false`, el locale se trata como vacío y `AUTO` cae en `settings.default-language`.

## País efectivo

El snapshot maneja:

- `detected_country`: país detectado por GeoIP o `XX`.
- `country_override`: país manual, vacío si no hay override.

`countryCode()` devuelve:

1. `country_override`, si es ISO alpha-2 válido y distinto de `XX`;
2. si no, `detected_country` normalizado;
3. fallback `XX`.

GeoIP no sobrescribe un país real ya guardado con `XX` cuando una nueva detección da desconocido. Sí actualiza cuando aparece un país real distinto o cuando antes estaba `XX`.

## Persistencia

La tabla real es `${tablePrefix}player_settings`:

```sql
player_uuid BINARY(16) NOT NULL
setting_key VARCHAR(64) NOT NULL
setting_value VARCHAR(255) NOT NULL
updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
PRIMARY KEY (player_uuid, setting_key)
```

`loadOrCreate` garantiza defaults persistidos para:

- `language = auto`
- `detected_country = XX`

No garantiza una fila default para `country_override`; el snapshot la representa como `""` si no existe.

## Mutaciones públicas

### `setLanguage`

1. Si la preferencia nueva es igual a la actual, devuelve future completado y no dispara evento.
2. Persiste `SettingKey.LANGUAGE` async.
3. Si falla persistencia, loguea error, el future falla, no cambia caché y no dispara evento.
4. Si persiste correctamente, agenda en main thread:
   - actualizar caché;
   - disparar `PlayerSettingChangeEvent` si cambió el valor almacenado o el idioma resuelto.

### `setCountryOverride`

1. Normaliza el código con `CountryFlag.normalizeCode`.
2. Rechaza `XX` e inválidos con `IllegalArgumentException` en future fallido.
3. Persiste async.
4. Si persiste, actualiza caché y dispara evento en main thread solo si cambió el país efectivo.

### `clearCountryOverride`

1. Persiste `country_override = ""` async.
2. Si persiste, actualiza caché y dispara evento en main thread solo si cambió el país efectivo.

### `setSetting`

Actualmente solo soporta `SettingKey.LANGUAGE` porque es la única clave con `playerWritable() == true`. Para cualquier otra clave devuelve future fallido.

## Eventos y threading

| Evento | Cuándo se dispara | Thread según implementación visible |
|---|---|---|
| `PlayerSettingsReadyEvent` | Durante `PlayerJoinEvent`, después de `readyPlayers.add(playerId)`. | Main thread, porque viene de `PlayerJoinEvent`. |
| `PlayerSettingChangeEvent` por mutaciones | Después de persistencia exitosa y actualización de caché. | Main thread, porque `DefaultPlayerSettingsService` usa scheduler `runTask`. |
| `PlayerSettingChangeEvent` por cambio de locale | En `PlayerLocaleChangeEvent` si preferencia `AUTO` cambia idioma efectivo. | El thread del evento Bukkit/Paper de locale; el código no reprograma explícitamente. |

Como consumidor, tratá eventos como señales de estado, no como transacciones de DB. Si necesitás operaciones costosas, salí del evento y hacé tu propio trabajo async.

## No asumir

- No asumas que `cached(UUID)` existe antes del ready event.
- No asumas que `getCachedOrDefault` implica que el jugador está cargado desde DB.
- No asumas que `country_override` puede escribirse con `setSetting`; usá `setCountryOverride` o `clearCountryOverride`.
- No asumas que PlaceholderAPI siempre refleja datos recién mutados si está dentro del TTL configurado.
