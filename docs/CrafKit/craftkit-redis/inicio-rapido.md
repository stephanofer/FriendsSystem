# Inicio rápido — `craftkit-redis`

Esta guía muestra el flujo mínimo para crear, usar y cerrar un `RedisClient` desde un plugin consumidor.

## Crear configuración

```java
RedisConfig config = RedisConfig.builder()
    .host("127.0.0.1")
    .port(6379)
    .database(0)
    .password("secret")
    .keyPrefix("hera")
    .environment("prod")
    .serverId("lobby-1")
    .build();
```

Campos recomendados por plugin:

- `host`
- `port`
- `database`
- `username` si Redis usa ACL
- `password`
- `ssl` / `verifyPeer` si aplica
- `keyPrefix`
- `environment`
- `serverId`

## Crear cliente

```java
RedisClient redis = RedisClients.lettuce(config);
```

`RedisClients.lettuce(...)` crea internamente:

- `DefaultClientResources` de Lettuce;
- `io.lettuce.core.RedisClient`;
- conexión principal de comandos;
- `RedisCoordinator` para leases.

La conexión Pub/Sub se crea de forma lazy en el primer `subscribe(...)` o `subscribePattern(...)`.

## Usar keys convencionales

```java
String key = redis.key("player", "session", playerUuid.toString());
```

Con `keyPrefix = hera` y `environment = prod`, produce:

```text
hera:prod:player:session:<uuid>
```

## Guardar caché temporal

```java
redis.cache()
    .set(key, "online", Duration.ofMinutes(5))
    .thenAccept(stored -> {
        if (!stored) {
            // Redis no respondió con OK.
        }
    });
```

`set(...)` requiere TTL. No existe `set` persistente en v1.

## Leer datos

```java
redis.cache().get(key).thenAccept(value -> {
    if (value == null) {
        // La key no existe.
        return;
    }

    // Usar valor.
});
```

La API actual devuelve `CompletableFuture<String>`. Redis puede devolver `null` cuando la key no existe.

## Publicar evento

```java
String channel = redis.channel("party", "member-joined");

redis.publisher().publish(channel, "{\"player\":\"...\",\"sourceServerId\":\"lobby-1\"}");
```

## Suscribirse a eventos

```java
RedisSubscription subscription = redis.subscriber().subscribe(channel, message -> {
    String payload = message.payload();

    // Callback async: no tocar Paper API directamente aquí.
});
```

Para dejar de recibir mensajes:

```java
subscription.close();
```

## Usar un lease seguro

```java
redis.coordinator().withLease(
    redis.key("matchmaking", "processor", "solo"),
    Duration.ofSeconds(10),
    () -> processQueueAsync()
).thenAccept(result -> {
    if (result.isEmpty()) {
        // Otro servidor obtuvo el lease.
        return;
    }

    // Esta instancia ejecutó la tarea.
});
```

`withLease(...)` libera el lease al terminar la tarea, incluso si la tarea falla.

## Cerrar en shutdown

```java
redis.close();
```

El cierre es idempotente. Después de `close()`, las operaciones fallan con `RedisException` o `CompletableFuture` fallido según el caso.
