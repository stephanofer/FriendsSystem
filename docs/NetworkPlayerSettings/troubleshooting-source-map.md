# Troubleshooting y mapa de fuentes

## Problemas comunes de integración

### `PlayerSettingsService` es `null`

Verificá:

- Tu `plugin.yml`/metadata declara `depend: [NetworkPlayerSettings]` si la integración es obligatoria.
- NetworkPlayerSettings inició correctamente; si falla config, assets, DB o zMenu, se deshabilita y no registra servicio.
- El nombre exacto del plugin es `NetworkPlayerSettings`.

### `NetworkAssetService` es `null`

Posibles causas:

- El catálogo `assets/countries.yml` es inválido.
- Falta el fallback `XX`.
- Hay aliases duplicados o que colisionan con códigos.
- El plugin falló antes/durante bootstrap de assets.

### El jugador aparece con país `XX`

`XX` es el fallback real. Puede pasar si:

- GeoIP está deshabilitado por config.
- `GeoLite2-Country.mmdb` no existe en la ruta configurada.
- La IP es local/no pública.
- MaxMind no encontró país.
- Hubo error resolviendo la IP.

Si ya había un país real guardado, una detección desconocida no debería pisarlo.

### El idioma efectivo no coincide con el cliente

Revisá:

- `settings.detect-client-locale` debe estar en `true` para que `AUTO` use locale del cliente.
- La preferencia manual `es` o `en` ignora el locale del cliente.
- Solo locales `es`, `es_*`, `en`, `en_*` se reconocen directamente; el resto cae en `settings.default-language`.

### `setSetting` falla para país

Es esperado. En `SettingKey`, solo `LANGUAGE` tiene `playerWritable() == true`. Para país usá:

- `setCountryOverride(UUID, String)`
- `clearCountryOverride(UUID)`

### Una mutación no cambió caché

Las mutaciones actualizan caché solo después de persistencia exitosa y ejecución de la continuación en main thread. Si el future falla, el caché conserva el valor anterior.

### PlaceholderAPI no muestra valores

Verificá:

- PlaceholderAPI está instalado y habilitado.
- `placeholderapi.enabled: true`.
- Usás el identificador `playersettings`.
- El placeholder existe: `language`, `language_preference`, `language_name`, `country`.

## Mapa de fuentes para mantener la documentación

| Tema | Archivo fuente |
|---|---|
| Build/dependencias/shading | `build.gradle.kts` |
| Nombre del plugin y dependencias runtime | `src/main/resources/plugin.yml` |
| Lifecycle principal | `src/main/java/com/stephanofer/networkplayersettings/NetworkPlayerSettingsPlugin.java` |
| API pública de ajustes | `src/main/java/com/stephanofer/networkplayersettings/api/PlayerSettingsService.java` |
| Snapshot y defaults | `src/main/java/com/stephanofer/networkplayersettings/api/PlayerSettingsSnapshot.java` |
| Idiomas | `Language.java`, `LanguagePreference.java`, `language/LanguageResolver.java` |
| Setting keys | `api/SettingKey.java` |
| País/flags | `api/CountryFlag.java`, `api/CountryAsset.java` |
| Servicio de assets | `api/NetworkAssetService.java`, `asset/*` |
| Eventos públicos | `event/PlayerSettingsReadyEvent.java`, `event/PlayerSettingChangeEvent.java` |
| Servicio principal | `service/DefaultPlayerSettingsService.java` |
| Listeners de conexión | `listener/PlayerConnectionListener.java` |
| Repositorio SQL | `repository/SqlPlayerSettingsRepository.java` |
| Config model | `config/PluginConfig.java` |
| YAML loader | `yaml/PluginYamlLoader.java` |
| Config default | `src/main/resources/config.yml` |
| Países default | `src/main/resources/assets/countries.yml` |
| Migraciones | `src/main/resources/db/migration/*.sql` |
| PlaceholderAPI | `placeholder/PlayerSettingsPlaceholderExpansion.java` |
| Comando | `command/GlobalSettingsCommand.java` |
| zMenu/bootstrap | `menu/*`, `src/main/resources/inventories/language.yml` |
| Tests de contratos de servicio | `src/test/java/com/stephanofer/networkplayersettings/service/DefaultPlayerSettingsServiceTest.java` |
| Tests de assets | `src/test/java/com/stephanofer/networkplayersettings/asset/*Test.java` |

## Checklist de verificación al cambiar el proyecto

- [ ] Si cambia `PlayerSettingsService`, actualizar `api-publica.md` y ejemplos.
- [ ] Si cambia `PluginConfig` o `config.yml`, actualizar `configuracion.md`.
- [ ] Si cambia `DefaultPlayerSettingsService`, revisar flujo, eventos, async y do/don't.
- [ ] Si cambia PlaceholderAPI, actualizar `assets-placeholderapi-ui.md`.
- [ ] Si cambia `assets/countries.yml` o loader/catalog, actualizar reglas de assets.
- [ ] Si cambia plugin metadata, actualizar instalación/integración.
