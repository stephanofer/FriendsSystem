# Nick Styles y Chat Styles

ProxySettings carga los catálogos de nick y chat styles desde YAML y permite renderizarlos para plugins Velocity consumidores.

## Archivos relevantes

| Área | Archivo |
|---|---|
| Servicio runtime | `src/main/java/com/stephanofer/proxysettings/style/PlayerStyleService.java` |
| Patrón interno | `src/main/java/com/stephanofer/proxysettings/style/StylePattern.java` |
| Metadata pública | `src/main/java/com/stephanofer/proxysettings/style/StylePatternInfo.java` |
| Catálogo | `src/main/java/com/stephanofer/proxysettings/style/StylePatternCatalog.java` |
| Loader YAML | `src/main/java/com/stephanofer/proxysettings/style/StylePatternCatalogLoader.java` |
| Renderer | `src/main/java/com/stephanofer/proxysettings/style/StylePatternRenderer.java` |
| Nick YAML | `src/main/resources/styles/nick-patterns.yml` |
| Chat YAML | `src/main/resources/styles/chat-patterns.yml` |

## Formato YAML

```yaml
patterns:
  red:
    display-name: "<red>Red"
    category: "basic"
    permission: "networkplayersettings.nick.red"
    mini-message: "<red><name></red>"
    preview: "Vendimia"
```

Para chat:

```yaml
patterns:
  gray-clean:
    display-name: "<gray>Gray Clean"
    category: "basic"
    permission: "networkplayersettings.chat.gray-clean"
    mini-message: "<gray><message></gray>"
    preview: "This is my message"
```

## Validaciones de patrón

`StylePattern` valida:

- ID no vacío;
- ID normalizado a minúsculas;
- ID cumple `[a-z0-9_-]{2,64}`;
- `display-name` no vacío;
- `category` no vacía y normalizada a minúsculas;
- `mini-message` no vacío;
- `preview` no vacío;
- nick styles contienen `<name>`;
- chat styles contienen `<message>`.

`permission` puede estar vacío.

## Validaciones de catálogo

`StylePatternCatalog` valida:

- el catálogo no está vacío;
- todos los patterns tienen el tipo esperado;
- no hay IDs duplicados.

Si un catálogo es inválido, el startup falla.

## `StylePatternInfo`

Record público:

```java
public record StylePatternInfo(
    String id,
    String displayName,
    String category,
    String permission,
    String previewText
) {}
```

Notas:

- `displayName` puede contener MiniMessage;
- `category` es string, no enum cerrado;
- `permission` se expone como metadata, pero ProxySettings no valida permisos;
- `previewText` es el texto base definido por YAML.

## Permisos

ProxySettings no valida permisos en Velocity.

Decisión actual:

- Bukkit/Paper controla qué style puede seleccionar un jugador;
- si un style quedó guardado en DB, Velocity confía en ese valor;
- si el jugador pierde permiso después, ProxySettings no lo detecta ni lo bloquea.

Esto mantiene el MVP simple y evita depender de LuckPerms u otro sistema de permisos en este módulo.

## Catálogos públicos

```java
List<StylePatternInfo> nickPatterns = api.nickPatterns();
List<StylePatternInfo> chatPatterns = api.chatPatterns();
```

Buscar por ID:

```java
Optional<StylePatternInfo> nick = api.nickPattern("premium-gold");
Optional<StylePatternInfo> chat = api.chatPattern("gray-clean");
```

La búsqueda por ID es case-insensitive con trim.

## Style seleccionado

```java
Optional<String> nickStyle = api.nickStyleId(playerId);
Optional<String> chatStyle = api.chatStyleId(playerId);
```

Devuelve `Optional.empty()` si el valor persistido está vacío.

Estos métodos leen desde snapshot/cache/default. No consultan DB por sí mismos.

## Render de nick

```java
Component displayName = api.formattedNick(playerId, username);
```

Comportamiento real:

- lee `nick_style` del snapshot;
- busca el patrón en el catálogo de nick;
- si existe, deserializa `mini-message` con `Placeholder.component("name", Component.text(username))`;
- si no existe o no hay style, devuelve `Component.text(username)`;
- si `username` es `null`, usa `""`.

## Render de chat

```java
Component raw = Component.text(messageText);
Component rendered = api.formatChatMessage(playerId, raw).orElse(raw);
```

Comportamiento real:

- lee `chat_style` del snapshot;
- busca el patrón en el catálogo de chat;
- si existe, deserializa `mini-message` con `Placeholder.component("message", message)`;
- si no existe o no hay style, devuelve `Optional.empty()`;
- si `message` es `null`, el renderer usa `Component.empty()`.

## Seguridad del texto de usuario

El renderer inserta el mensaje como `Placeholder.component`, no como texto parseado.

Para mensajes escritos por jugadores, el consumidor debería construir un `Component.text(...)` con texto normalizado/sanitizado antes de pasarlo a `formatChatMessage`.

Ejemplo:

```java
String normalized = message.trim();
Component raw = Component.text(normalized);
Component styled = api.formatChatMessage(senderId, raw).orElse(raw);
```

No pases texto de usuario como MiniMessage parseado salvo que tu plugin realmente quiera permitir tags.

## No hay integración automática con chat Velocity

ProxySettings no escucha eventos de chat de Velocity.

Los consumidores deben llamar `formatChatMessage(...)` donde corresponda:

- mensajes privados;
- broadcasts sociales;
- listados;
- sistemas de chat custom;
- notificaciones.
