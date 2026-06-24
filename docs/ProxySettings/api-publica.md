# API Pública

La API pública de ProxySettings está pensada para plugins Velocity consumidores.

Paquete principal:

```java
com.stephanofer.proxysettings.api
```

## Obtener la API

Clase:

```java
ProxySettingsProvider
```

Métodos:

```java
public static ProxySettingsApi api()
public static Optional<ProxySettingsApi> optional()
```

### Uso estricto

```java
ProxySettingsApi settings = ProxySettingsProvider.api();
```

Si la API no está registrada, lanza:

```text
IllegalStateException: ProxySettings API is not available
```

### Uso tolerante

```java
Optional<ProxySettingsApi> optional = ProxySettingsProvider.optional();
if (optional.isEmpty()) {
    // Desactivar integración o usar fallback propio.
    return;
}

ProxySettingsApi settings = optional.get();
```

Para plugins que pueden funcionar sin ProxySettings, usar `optional()` es más seguro.

## `ProxySettingsApi`

Archivo real:

```text
src/main/java/com/stephanofer/proxysettings/api/ProxySettingsApi.java
```

Interfaz actual:

```java
public interface ProxySettingsApi {
    CompletableFuture<PlayerSettingsSnapshot> load(UUID playerId);
    CompletableFuture<Map<UUID, PlayerSettingsSnapshot>> loadMany(Collection<UUID> playerIds);
    Optional<PlayerSettingsSnapshot> cached(UUID playerId);
    PlayerSettingsSnapshot getCachedOrDefault(UUID playerId);
    Optional<String> setting(UUID playerId, SettingKey key);
    Language resolvedLanguage(UUID playerId, java.util.Locale clientLocale);
    String countryCode(UUID playerId);
    boolean showCountryFlag(UUID playerId);
    CountryAsset countryAsset(UUID playerId);
    String countryHeadTexture(UUID playerId);
    Component countryFlag(UUID playerId);
    TagResolver countryFlagResolver(UUID playerId);
    List<StylePatternInfo> nickPatterns();
    List<StylePatternInfo> chatPatterns();
    Optional<StylePatternInfo> nickPattern(String patternId);
    Optional<StylePatternInfo> chatPattern(String patternId);
    Optional<String> nickStyleId(UUID playerId);
    Optional<String> chatStyleId(UUID playerId);
    Component formattedNick(UUID playerId, String username);
    Optional<Component> formatChatMessage(UUID playerId, Component message);
    void invalidate(UUID playerId);
}
```

## Carga y cache

### `load(UUID playerId)`

Carga el snapshot de un jugador.

Comportamiento real:

- si el jugador ya está en cache, devuelve un `CompletableFuture` completado con el snapshot cacheado;
- si no está en cache, consulta DB y guarda el resultado en cache;
- requiere `playerId` no nulo.

Uso recomendado:

```java
settings.load(playerId).thenAccept(snapshot -> {
    String country = snapshot.countryCode();
});
```

No bloquear el event loop del proxy con `join()` en comandos o eventos frecuentes.

### `loadMany(Collection<UUID> playerIds)`

Carga varios snapshots y devuelve un mapa `UUID -> PlayerSettingsSnapshot`.

Comportamiento real:

- ignora UUIDs nulos;
- deduplica UUIDs;
- solo consulta DB para los que no están en cache;
- hace una sola query con `WHERE player_uuid IN (...)` para los faltantes;
- devuelve defaults para entradas que no existan en DB.

Uso recomendado para listas:

```java
settings.loadMany(friendIds).thenAccept(snapshots -> {
    for (UUID friendId : friendIds) {
        PlayerSettingsSnapshot snapshot = snapshots.get(friendId);
    }
});
```

Esto evita hacer una consulta por cada jugador visible en una página.

### `cached(UUID playerId)`

Devuelve el snapshot cacheado si existe.

No consulta DB.

```java
Optional<PlayerSettingsSnapshot> cached = settings.cached(playerId);
```

### `getCachedOrDefault(UUID playerId)`

Devuelve cache si existe. Si no existe, devuelve defaults en memoria.

No consulta DB.

Esto es útil para renderizado tolerante, pero no debe confundirse con “dato cargado desde DB”.

### `invalidate(UUID playerId)`

Elimina el snapshot de la cache si el UUID no es nulo.

No recarga automáticamente.

## Settings directos

### `setting(UUID playerId, SettingKey key)`

Lee un setting desde `getCachedOrDefault(playerId)`.

No consulta DB.

Devuelve `Optional.empty()` si el valor está vacío.

```java
Optional<String> nickStyle = settings.setting(playerId, SettingKey.NICK_STYLE);
```

## Idioma

### `resolvedLanguage(UUID playerId, Locale clientLocale)`

Resuelve el idioma efectivo usando:

- preferencia guardada en snapshot/cache/default;
- locale recibido desde el consumidor;
- `settings.default-language`;
- `settings.detect-client-locale`.

```java
Language language = settings.resolvedLanguage(player.getUniqueId(), player.getEffectiveLocale());
```

## País y bandera

### `countryCode(UUID playerId)`

Devuelve el país efectivo desde el snapshot:

1. `country_override` válido y distinto de `XX`;
2. si no, `detected_country` normalizado;
3. fallback `XX`.

### `showCountryFlag(UUID playerId)`

Devuelve el booleano de `show_country_flag`.

Default: `true`.

### `countryAsset(UUID playerId)`

Devuelve el asset del país efectivo.

Si el país no existe en catálogo, cae al asset `XX`.

Importante: este método no oculta el asset aunque `show_country_flag` sea `false`. Solo resuelve el asset.

### `countryHeadTexture(UUID playerId)`

Devuelve el base64 de la textura de bandera.

Si `show_country_flag` es `false`, devuelve `""`.

### `countryFlag(UUID playerId)`

Devuelve un `Component` Adventure con `ObjectContents.playerHead()`.

Si `show_country_flag` es `false`, devuelve `Component.empty()`.

### `countryFlagResolver(UUID playerId)`

Devuelve un `TagResolver` para el tag:

```text
<country_flag>
```

Si `show_country_flag` es `false`, el tag inserta `Component.empty()`.

## Styles

### Catálogos

```java
List<StylePatternInfo> nickPatterns();
List<StylePatternInfo> chatPatterns();
Optional<StylePatternInfo> nickPattern(String patternId);
Optional<StylePatternInfo> chatPattern(String patternId);
```

Los catálogos son los cargados al startup desde:

- `styles/nick-patterns.yml`;
- `styles/chat-patterns.yml`.

### Selección guardada

```java
Optional<String> nickStyleId(UUID playerId);
Optional<String> chatStyleId(UUID playerId);
```

Devuelven el ID guardado en el snapshot si no está vacío.

No validan permisos.

### Render de nick

```java
Component formattedNick(UUID playerId, String username);
```

Comportamiento:

- si el jugador tiene `nick_style` y existe en catálogo, renderiza el patrón con `<name>`;
- si no hay style válido, devuelve `Component.text(username)`;
- si `username` es `null`, usa string vacío.

### Render de chat

```java
Optional<Component> formatChatMessage(UUID playerId, Component message);
```

Comportamiento:

- si el jugador tiene `chat_style` y existe en catálogo, devuelve el mensaje renderizado;
- si no hay style válido, devuelve `Optional.empty()`;
- si `message` es `null`, el renderer usa `Component.empty()`.

Uso típico:

```java
Component raw = Component.text(messageText);
Component rendered = settings.formatChatMessage(senderId, raw).orElse(raw);
```

## Tipos públicos importantes

| Tipo | Paquete | Uso |
|---|---|---|
| `ProxySettingsApi` | `com.stephanofer.proxysettings.api` | API principal. |
| `ProxySettingsProvider` | `com.stephanofer.proxysettings.api` | Lookup estático de API. |
| `PlayerSettingsSnapshot` | `com.stephanofer.proxysettings.settings` | Snapshot inmutable de settings. |
| `SettingKey` | `com.stephanofer.proxysettings.settings` | Keys soportadas. |
| `Language` | `com.stephanofer.proxysettings.language` | Idiomas resueltos. |
| `LanguagePreference` | `com.stephanofer.proxysettings.language` | Preferencia persistida. |
| `CountryAsset` | `com.stephanofer.proxysettings.country` | Asset de país. |
| `StylePatternInfo` | `com.stephanofer.proxysettings.style` | Metadata pública de style. |
