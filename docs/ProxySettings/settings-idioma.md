# Settings e Idioma

ProxySettings lee settings persistidos por `NetworkPlayerSettings` desde MySQL y los expone como `PlayerSettingsSnapshot`.

## Tabla esperada

La tabla efectiva es:

```text
${database.table-prefix}player_settings
```

Con la config default:

```text
nps_player_settings
```

Query individual real:

```sql
SELECT setting_key, setting_value
FROM ${prefix}player_settings
WHERE player_uuid = ?
```

Query por lote real:

```sql
SELECT player_uuid, setting_key, setting_value
FROM ${prefix}player_settings
WHERE player_uuid IN (?, ?, ...)
```

`player_uuid` se maneja como `BINARY(16)`.

## `SettingKey`

Archivo real:

```text
src/main/java/com/stephanofer/proxysettings/settings/SettingKey.java
```

Keys actuales:

| Enum | Storage key | Default |
|---|---|---|
| `LANGUAGE` | `language` | `auto` |
| `DETECTED_COUNTRY` | `detected_country` | `XX` |
| `COUNTRY_OVERRIDE` | `country_override` | `""` |
| `SHOW_COUNTRY_FLAG` | `show_country_flag` | `true` |
| `NICK_STYLE` | `nick_style` | `""` |
| `CHAT_STYLE` | `chat_style` | `""` |

`SettingKey.fromStorageKey(String)` hace búsqueda case-insensitive y devuelve `null` si la key no existe.

## `PlayerSettingsSnapshot`

Archivo real:

```text
src/main/java/com/stephanofer/proxysettings/settings/PlayerSettingsSnapshot.java
```

Características:

- inmutable hacia afuera;
- siempre rellena defaults para todas las keys conocidas;
- trimmea valores entrantes;
- convierte valores `null` en `""`;
- `values()` devuelve un mapa no modificable.

Métodos principales:

```java
UUID playerId()
Map<SettingKey, String> values()
Optional<String> setting(SettingKey key)
String valueOrDefault(SettingKey key)
LanguagePreference languagePreference()
String detectedCountryCode()
Optional<String> countryOverride()
String countryCode()
boolean showCountryFlag()
```

## Resolución de idioma

Tipos:

```java
Language
LanguagePreference
LanguageResolver
```

### `Language`

Idiomas actuales:

| Enum | Código | Nombre ES | Nombre EN |
|---|---|---|---|
| `SPANISH` | `es` | `Español` | `Spanish` |
| `ENGLISH` | `en` | `Inglés` | `English` |

`Language.fromCode(String)` devuelve:

- `SPANISH` solo si el valor es `es`;
- `ENGLISH` para cualquier otro valor.

`Language.fromLocale(Locale, Language defaultLanguage)` devuelve:

- `SPANISH` si `locale.getLanguage()` es `es`;
- `ENGLISH` si `locale.getLanguage()` es `en`;
- `defaultLanguage` para cualquier otro locale o `null`.

### `LanguagePreference`

Preferencias persistidas:

| Enum | Storage value |
|---|---|
| `AUTO` | `auto` |
| `SPANISH` | `es` |
| `ENGLISH` | `en` |

`LanguagePreference.fromStorage(String)` devuelve:

- `SPANISH` para `es`;
- `ENGLISH` para `en`;
- `AUTO` para `null`, blanco o desconocido.

### `LanguageResolver`

Reglas reales:

```java
SPANISH -> Language.SPANISH
ENGLISH -> Language.ENGLISH
AUTO -> detectClientLocale ? Language.fromLocale(clientLocale, defaultLanguage) : defaultLanguage
```

## Ejemplo para consumidor Velocity

```java
ProxySettingsApi api = ProxySettingsProvider.api();

Language language = api.resolvedLanguage(
    player.getUniqueId(),
    player.getEffectiveLocale()
);
```

## Importante sobre cache

`resolvedLanguage(...)` no fuerza carga desde DB. Usa `getCachedOrDefault(...)`.

Si se llama antes de que el snapshot esté cargado, puede devolver defaults. En runtime normal, `PostLoginEvent` llama `refresh(...)`, pero si un plugin necesita certeza antes de renderizar algo crítico, debe encadenar `load(playerId)`.

Ejemplo seguro:

```java
api.load(player.getUniqueId()).thenAccept(snapshot -> {
    Language language = api.resolvedLanguage(player.getUniqueId(), player.getEffectiveLocale());
});
```
