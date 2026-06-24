# Guía para Plugins Consumidores

Este documento explica cómo integrar un plugin Velocity con ProxySettings sin duplicar SQL, cache ni renderizado.

## Cuándo usar ProxySettings

Usá ProxySettings si tu plugin Velocity necesita:

- mostrar mensajes en el idioma efectivo del jugador;
- mostrar el país efectivo del jugador;
- renderizar una bandera como player head component;
- respetar `show_country_flag`;
- mostrar nombres con nick style seleccionado;
- aplicar chat style en mensajes privados, broadcasts o sistemas propios;
- cargar settings para muchos jugadores en una página/lista.

No lo uses para:

- escribir settings en DB;
- validar permisos de styles;
- crear tablas;
- reemplazar `NetworkPlayerSettings` del lado Paper;
- esperar actualización inmediata Paper -> Velocity sin reconexión.

## Dependencia de compilación

ProxySettings se publica en Maven Local con estas coordenadas:

```kotlin
com.stephanofer:proxysettings:1.0
```

Comando para publicar:

```powershell
& "C:\Users\vendi\Documents\software-workspace\java-projects\herav2\ProxySettings\adventure\gradlew.bat" -p "C:\Users\vendi\Documents\software-workspace\java-projects\herav2\ProxySettings" publishToMavenLocal
```

Configuración recomendada en el plugin consumidor:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.stephanofer:proxysettings:1.0")
}
```

Usá `compileOnly`, no `implementation`, porque ProxySettings debe estar instalado como plugin separado en Velocity. El consumidor solo necesita compilar contra la API.

La publicación Maven Local usa el JAR normal de compilación. No uses ese JAR para deploy en Velocity; para deploy usá `target/ProxySettings-1.0.jar`.

El paquete público principal es:

```java
com.stephanofer.proxysettings.api
```

## Obtener la API con fallback

Para plugins que pueden funcionar parcialmente sin ProxySettings:

```java
Optional<ProxySettingsApi> optional = ProxySettingsProvider.optional();
if (optional.isEmpty()) {
    logger.warn("ProxySettings is not available; using fallback rendering");
    return;
}

ProxySettingsApi proxySettings = optional.get();
```

Para plugins que requieren ProxySettings obligatoriamente:

```java
ProxySettingsApi proxySettings = ProxySettingsProvider.api();
```

Si no está disponible, `api()` lanza `IllegalStateException`.

## Load order

El descriptor actual de ProxySettings tiene plugin id:

```text
proxysettings
```

Los plugins consumidores deben asegurarse de no usar `ProxySettingsProvider.api()` antes de que ProxySettings haya inicializado y registrado la API.

Velocity soporta dependencias entre plugins desde la annotation `@Plugin`:

```java
@Plugin(
    id = "myconsumer",
    name = "MyConsumer",
    version = "1.0",
    dependencies = @Dependency(id = "proxysettings")
)
public final class MyConsumerPlugin {
}
```

Si la integración es opcional:

```java
@Plugin(
    id = "myconsumer",
    name = "MyConsumer",
    version = "1.0",
    dependencies = @Dependency(id = "proxysettings", optional = true)
)
public final class MyConsumerPlugin {
}
```

Estrategia recomendada:

- si tu plugin requiere ProxySettings para funcionar, declaralo como dependencia obligatoria y usá `ProxySettingsProvider.api()`;
- si tu plugin puede funcionar sin ProxySettings, declaralo como dependencia opcional y usá `ProxySettingsProvider.optional()`;
- si no podés garantizar orden, usá `ProxySettingsProvider.optional()` y fallback;
- evitá resolver la API en inicializadores estáticos.

## Carga de datos para un jugador

En flujo normal, ProxySettings carga el snapshot en `PostLoginEvent`. Aun así, un consumidor puede forzar carga segura:

```java
proxySettings.load(player.getUniqueId()).thenAccept(snapshot -> {
    Language language = proxySettings.resolvedLanguage(
        player.getUniqueId(),
        player.getEffectiveLocale()
    );
});
```

No uses `join()` en comandos o eventos frecuentes.

## Idioma efectivo

```java
Language language = proxySettings.resolvedLanguage(
    player.getUniqueId(),
    player.getEffectiveLocale()
);
```

Si el jugador tiene `language = auto`, el resultado depende de:

- `settings.detect-client-locale`;
- locale del cliente Velocity;
- `settings.default-language`.

## Mostrar nombre con nick style

```java
Component displayName = proxySettings.formattedNick(
    targetId,
    targetUsername
);
```

Si el jugador no tiene style válido, devuelve `Component.text(targetUsername)`.

## Mostrar bandera

```java
Component flag = proxySettings.countryFlag(targetId);
```

Si `show_country_flag` está apagado, devuelve `Component.empty()`.

## Mostrar bandera + nick

```java
Component line = Component.text()
    .append(proxySettings.countryFlag(targetId))
    .append(Component.space())
    .append(proxySettings.formattedNick(targetId, targetUsername))
    .build();
```

Este patrón funciona aunque la bandera esté oculta.

## Usar `<country_flag>` en MiniMessage

```java
MiniMessage miniMessage = MiniMessage.builder()
    .editTags(tags -> tags.resolver(proxySettings.countryFlagResolver(targetId)))
    .build();

Component component = miniMessage.deserialize("<country_flag> <gray>Perfil</gray>");
```

Si el jugador ocultó su bandera, el tag inserta `Component.empty()`.

## Aplicar chat style

```java
Component rawMessage = Component.text(normalizedMessage);

Component renderedMessage = proxySettings
    .formatChatMessage(sender.getUniqueId(), rawMessage)
    .orElse(rawMessage);
```

`formatChatMessage` devuelve `Optional.empty()` si no hay style válido.

## Ejemplo: mensaje privado

```java
UUID senderId = sender.getUniqueId();
String senderName = sender.getUsername();

Component senderDisplay = proxySettings.formattedNick(senderId, senderName);
Component senderFlag = proxySettings.countryFlag(senderId);

Component rawMessage = Component.text(messageText.trim());
Component styledMessage = proxySettings
    .formatChatMessage(senderId, rawMessage)
    .orElse(rawMessage);

Component finalMessage = Component.text()
    .append(Component.text("Mensaje de "))
    .append(senderFlag)
    .append(Component.space())
    .append(senderDisplay)
    .append(Component.text(" > "))
    .append(styledMessage)
    .build();

target.sendMessage(finalMessage);
```

## Ejemplo: lista de amigos o jugadores

Para una página con varios jugadores, usá `loadMany(...)` antes de renderizar:

```java
List<UUID> visibleIds = visibleProfiles.stream()
    .map(Profile::uuid)
    .toList();

proxySettings.loadMany(visibleIds).thenAccept(snapshots -> {
    for (Profile profile : visibleProfiles) {
        Component line = Component.text()
            .append(proxySettings.countryFlag(profile.uuid()))
            .append(Component.space())
            .append(proxySettings.formattedNick(profile.uuid(), profile.username()))
            .build();

        viewer.sendMessage(line);
    }
});
```

Esto reduce la presión sobre MySQL porque `loadMany` consulta en lote solo los jugadores faltantes en cache.

## Ejemplo: integración con sistema propio de mensajes YAML

Si tu sistema de mensajes soporta `TagResolver`, podés mezclar strings y components.

Ejemplo conceptual:

```yaml
list.entry: "<country_flag> <player_display> <dark_gray>·</dark_gray> <gray>{status}</gray>"
```

Render:

```java
TagResolver resolver = TagResolver.resolver(
    proxySettings.countryFlagResolver(profile.uuid()),
    Placeholder.component(
        "player_display",
        proxySettings.formattedNick(profile.uuid(), profile.username())
    )
);

Component component = miniMessage.deserialize(rawTemplate, resolver);
```

Si tu sistema actual solo reemplaza strings, conviene extenderlo para aceptar `Component` o `TagResolver`. Serializar components a string MiniMessage no está implementado por ProxySettings.

## Do

- Usá `loadMany(...)` para páginas y listas.
- Usá `Component.text(...)` para texto escrito por jugadores antes de aplicar chat style.
- Usá `countryFlag(...)` o `countryFlagResolver(...)` para respetar `show_country_flag`.
- Usá `optional()` si tu plugin puede funcionar sin ProxySettings.
- Tratá `getCachedOrDefault(...)` como fallback, no como garantía de carga DB.

## Don't

- No leas `nps_player_settings` directamente desde cada plugin consumidor.
- No dupliques loaders de `nick-patterns.yml`, `chat-patterns.yml` o `countries.yml` en consumidores.
- No bloquees eventos Velocity con `join()`.
- No esperes que cambios hechos en Paper se reflejen inmediatamente en Velocity sin reconexión.
- No dependas de permisos en ProxySettings para decidir si un style se aplica.
