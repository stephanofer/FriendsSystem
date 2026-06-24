# Referencia de Archivos

Mapa rápido para ubicar cada parte de ProxySettings.

## Build

| Archivo | Propósito |
|---|---|
| `build.gradle.kts` | Plugins Gradle, dependencias, Java 25, publicación Maven Local, shadowJar, relocations y salida a `target/`. |
| `gradle/libs.versions.toml` | Versiones centralizadas. |
| `settings.gradle.kts` | Configuración del proyecto Gradle. |
| `src/main/templates/com/stephanofer/proxysettings/BuildConstants.java` | Template para exponer la versión en `@Plugin`. |

## Recursos

| Archivo | Propósito |
|---|---|
| `src/main/resources/config.yml` | Config default. |
| `src/main/resources/assets/countries.yml` | Catálogo default de países/assets. |
| `src/main/resources/styles/nick-patterns.yml` | Catálogo default de nick styles. |
| `src/main/resources/styles/chat-patterns.yml` | Catálogo default de chat styles. |

## API

| Archivo | Propósito |
|---|---|
| `api/ProxySettingsApi.java` | Contrato público para consumidores. |
| `api/ProxySettingsProvider.java` | Provider estático para obtener la API. |

## Bootstrap y plataforma

| Archivo | Propósito |
|---|---|
| `ProxySettingsPlugin.java` | Plugin Velocity, bootstrap, registro de API y shutdown. |
| `platform/velocity/VelocityConnectionListener.java` | Carga snapshot en login e invalida en disconnect. |

## Configuración

| Archivo | Propósito |
|---|---|
| `config/ProxySettingsConfig.java` | Modelo de config y construcción de `DatabaseConfig`. |
| `config/ProxySettingsYamlLoader.java` | Carga de `config.yml`, países y styles con BoostedYAML. |

## Settings

| Archivo | Propósito |
|---|---|
| `settings/DefaultProxySettingsApi.java` | Implementación de API, cache Caffeine y delegación a servicios. |
| `settings/PlayerSettingsSnapshot.java` | Snapshot inmutable con defaults. |
| `settings/SettingKey.java` | Keys persistidas soportadas. |
| `settings/storage/PlayerSettingsRepository.java` | Interfaz de repositorio. |
| `settings/storage/SqlPlayerSettingsRepository.java` | Lectura SQL individual y por lote. |
| `settings/storage/SqlUuid.java` | Conversión UUID <-> `BINARY(16)`. |

## Idioma

| Archivo | Propósito |
|---|---|
| `language/Language.java` | Idiomas soportados y resolución desde code/locale. |
| `language/LanguagePreference.java` | Preferencia persistida. |
| `language/LanguageResolver.java` | Resolución final según preferencia y locale. |

## Países y banderas

| Archivo | Propósito |
|---|---|
| `country/CountryFlag.java` | Normalización ISO alpha-2 y fallback `XX`. |
| `country/CountryAsset.java` | Modelo público de asset. |
| `country/CountryAssetCatalog.java` | Catálogo inmutable por código/alias. |
| `country/CountryAssetLoader.java` | Loader y validaciones YAML. |
| `country/CountryFlagService.java` | Base64, `Component` y `TagResolver` de bandera. |

## Styles

| Archivo | Propósito |
|---|---|
| `style/StylePatternType.java` | Tipos `NICK` y `CHAT`. |
| `style/StylePattern.java` | Modelo interno validado. |
| `style/StylePatternInfo.java` | Metadata pública. |
| `style/StylePatternCatalog.java` | Catálogo inmutable. |
| `style/StylePatternCatalogLoader.java` | Loader YAML. |
| `style/StylePatternRenderer.java` | Render Adventure/MiniMessage. |
| `style/PlayerStyleService.java` | Servicio de catálogo y render por jugador. |
