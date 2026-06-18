# Assets de países, PlaceholderAPI, comandos y UI

Este documento cubre puntos de integración complementarios para plugins consumidores.

## `NetworkAssetService`

NetworkPlayerSettings registra `NetworkAssetService` durante startup después de cargar `assets/countries.yml`.

```java
NetworkAssetService assets = Bukkit.getServicesManager().load(NetworkAssetService.class);
CountryAsset argentina = assets.countryAsset("AR");
CountryAsset byAlias = assets.countryAsset("argentina");
CountryAsset fallback = assets.countryAsset("no-existe"); // XX
```

Comportamiento:

- `countryAsset(String)` acepta código canónico o alias.
- Códigos se buscan en mayúsculas; aliases en minúsculas.
- `null`, strings blancos o desconocidos devuelven `unknownCountryAsset()`.
- `countryAssets()` expone un mapa inmutable con códigos canónicos, no aliases.
- Las búsquedas de gameplay son en memoria; el catálogo se carga una vez al bootstrap.

## Flags por emoji

`CountryFlag` no depende del catálogo YAML. Sirve para normalizar y renderizar flags:

```java
String code = CountryFlag.normalizeCode(settings.countryCode(playerId));
String flag = CountryFlag.emoji(code);
```

Fallbacks:

- Código inválido o `null` -> `XX`.
- Emoji de `XX` -> `🏳`.

## PlaceholderAPI

La expansión se registra solo si:

1. `placeholderapi.enabled: true`;
2. el plugin PlaceholderAPI está instalado y habilitado.

Metadata real: `softdepend: [PlaceholderAPI]`.

Identificador de expansión: `playersettings`.

| Placeholder | Valor |
|---|---|
| `%playersettings_language%` | Idioma efectivo (`es`/`en`) para jugador online. Si no hay jugador online, fallback `en`. |
| `%playersettings_language_preference%` | Preferencia guardada/cacheada (`auto`/`es`/`en`). Si no hay jugador/UUID, fallback `auto`. |
| `%playersettings_language_name%` | Nombre del idioma efectivo visto desde ese idioma. Offline fallback `English`. |
| `%playersettings_country%` | País efectivo (`AR`, `XX`, etc.). Si no hay jugador/UUID, fallback `XX`. |

Los placeholders se cachean por `playerId:param` durante `placeholderapi.cache-ttl-millis` si el TTL es positivo. Con TTL `0` o negativo, no se cachean.

## Comando de usuario

El comando se registra con Cloud Paper. Defaults:

- `/globalsettings`
- aliases `/settings`, `/prefs`
- `/globalsettings help [query]`

Solo jugadores pueden ejecutar el comando principal. El handler:

1. comprueba `settingsService.isReady(player.getUniqueId())`;
2. si no está listo, envía `settings.loading`;
3. si está listo, intenta abrir el target configurado;
4. si falla, envía `settings.menu-open-failed`.

## Menú de idioma

El menú interno se carga con zMenu desde `inventories/language.yml` y registra un botón custom `NPS_LANGUAGE`.

El botón:

- cambia la preferencia mediante `settingsService.setLanguage`;
- aplica cooldown por jugador con `settings.language-change-cooldown-millis`;
- limpia cooldown al salir el jugador;
- renderiza selección con enchant visual;
- usa placeholders internos de zMenu, no PlaceholderAPI.

Limitación importante: el click llama `setLanguage(...)` pero no encadena el `CompletableFuture` para confirmar al jugador después de persistir. Esto es comportamiento interno del menú; para plugins consumidores que muten ajustes, sí se recomienda esperar el future antes de mostrar confirmaciones críticas.

## Dialogs zMenu

`SettingsMenuBootstrap` carga `.dialogs("dialogs")` y `SettingsViewOpener` soporta `command.open.type: dialog`, pero este repositorio no contiene recursos bajo `src/main/resources/dialogs`. Si configurás `dialog` sin recursos/dialog manager disponible, abrir el comando puede fallar y enviar `settings.menu-open-failed`.

Para consumidores, esto no es un extension point público. Si querés abrir tu propia UI, hacelo desde tu plugin usando `PlayerSettingsService` en vez de acoplarte a `SettingsViewOpener`.
