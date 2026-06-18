# NetworkPlayerSettings: guía de integración para plugins consumidores

NetworkPlayerSettings centraliza ajustes globales de jugador para una network Minecraft/Paper: idioma preferido, idioma resuelto, país detectado, override manual de país y assets de países. Esta documentación está escrita para autores de plugins consumidores que quieren depender de este proyecto sin usar clases internas ni asumir estados que todavía no están listos.

## Ruta rápida

1. Declarar dependencia del plugin `NetworkPlayerSettings` en tu metadata de Paper/Bukkit.
2. Agregar la dependencia de compilación desde Maven Local: `com.stephanofer:networkplayersettings:1.0.0-SNAPSHOT`.
3. Obtener `PlayerSettingsService` desde `ServicesManager` cuando tu plugin esté habilitado.
4. Esperar `PlayerSettingsReadyEvent` o comprobar `PlayerSettingsService#isReady(UUID)` antes de leer datos de un jugador conectado.
5. Usar solo el paquete público `com.stephanofer.networkplayersettings.api` y los eventos en `com.stephanofer.networkplayersettings.event`.
6. Tratar las mutaciones como operaciones persistentes asíncronas: usar el `CompletableFuture` y no bloquear el main thread.

```java
PlayerSettingsService settings = Bukkit.getServicesManager().load(PlayerSettingsService.class);
if (settings == null) {
    throw new IllegalStateException("NetworkPlayerSettings service is not available");
}
```

## Qué provee el proyecto

| Área | Qué expone para consumidores |
|---|---|
| Ajustes de jugador | `PlayerSettingsService`, `PlayerSettingsSnapshot`, `SettingKey`, `LanguagePreference`, `Language`. |
| Eventos | `PlayerSettingsReadyEvent` y `PlayerSettingChangeEvent`. |
| Países y flags | `CountryFlag`, `CountryAsset`, `NetworkAssetService`. |
| PlaceholderAPI | Expansión `%playersettings_*%` si PlaceholderAPI está instalada y la config lo permite. |
| UI/comandos | Comando configurable de ajustes globales y menú zMenu interno de idioma. No es una API de extensión pública. |

## Cuándo conviene depender de NetworkPlayerSettings

Usalo cuando tu plugin necesita:

- renderizar mensajes, menús o placeholders según el idioma global del jugador;
- consultar la preferencia guardada (`auto`, `es`, `en`);
- saber el país efectivo del jugador (`country_override` si existe, si no `detected_country`);
- acceder a assets de países por código ISO alpha-2 o alias;
- reaccionar cuando los ajustes quedan listos o cambian.

No lo uses para:

- escribir directamente en la base de datos `nps_player_settings`;
- llamar clases en paquetes internos como `service`, `repository`, `config`, `asset`, `menu`, `listener`, `placeholder`, `country`, `language` o `yaml`;
- asumir que el dato del jugador está listo durante `AsyncPlayerPreLoginEvent` de tu propio plugin sin coordinar el flujo.

## Documentos de esta carpeta

- [`instalacion-integracion.md`](instalacion-integracion.md): dependencias, plugin metadata, lookup del servicio y timing de lifecycle.
- [`api-publica.md`](api-publica.md): referencia completa de clases públicas de API y eventos.
- [`flujo-ajustes-eventos.md`](flujo-ajustes-eventos.md): carga, caché, persistencia, eventos y threading observado en el código.
- [`configuracion.md`](configuracion.md): `config.yml`, `assets/countries.yml`, recursos, defaults y consecuencias operativas.
- [`assets-placeholderapi-ui.md`](assets-placeholderapi-ui.md): assets de países, PlaceholderAPI, comandos y menú.
- [`ejemplos-y-buenas-practicas.md`](ejemplos-y-buenas-practicas.md): snippets correctos y do/don't para plugins consumidores.
- [`troubleshooting-source-map.md`](troubleshooting-source-map.md): errores comunes, diagnóstico y mapa de archivos fuente.

## Límites documentados explícitamente

- El repositorio publica para desarrollo local con `maven-publish`: `com.stephanofer:networkplayersettings:1.0.0-SNAPSHOT` mediante `publishToMavenLocal`.
- No hay repositorio Maven remoto configurado en el proyecto. Para builds compartidos fuera de la máquina local, publicá el artefacto en un Maven privado o usá otra estrategia controlada por tu pipeline.
- La metadata real del plugin usa `depend: [zMenu]` y `softdepend: [PlaceholderAPI]`; los consumidores deben declarar su relación con `NetworkPlayerSettings` según si la integración es obligatoria u opcional.
- El proyecto registra servicios Bukkit con `ServicePriority.Normal`; no hay API propia de bootstrap fuera de `ServicesManager`.
