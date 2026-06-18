# Pub/Sub entre servidores — `craftkit-redis`

`RedisPublisher` y `RedisSubscriber` entregan comunicación rápida entre servidores usando Redis Pub/Sub.

## Alcance

Pub/Sub en v1 es **no durable**.

Esto significa:

- Redis no guarda historial de mensajes Pub/Sub.
- Solo reciben el mensaje los servidores conectados y suscritos en ese momento.
- Si un servidor está reiniciando, desconectado o Redis cae durante la publicación, ese servidor puede perder el mensaje.

Usar Pub/Sub para avisos rápidos, invalidación de caché, señales de estado o eventos que puedan recalcularse desde Redis/DB.

No usar Pub/Sub como única fuente para recompensas, compras, transacciones o eventos que deben procesarse sí o sí.

## Channels convencionales

Crear channels con:

```java
String channel = redis.channel("party", "member-joined");
```

Formato real:

```text
<keyPrefix>:<environment>:events:<domain>:<event>
```

Ejemplo:

```text
hera:prod:events:party:member-joined
```

## Publicar

```java
redis.publisher().publish(channel, payload);
```

`publish(...)` devuelve `CompletableFuture<Long>` con el número de subscribers reportado por Redis.

El payload es `String`. Si el plugin quiere JSON, debe serializarlo antes de publicar.

Ejemplo:

```java
String payload = "{\"sourceServerId\":\"lobby-1\",\"player\":\"...\"}";

redis.publisher().publish(redis.channel("party", "member-joined"), payload);
```

## Suscribirse a un channel

```java
RedisSubscription subscription = redis.subscriber().subscribe(channel, message -> {
    String receivedChannel = message.channel();
    String payload = message.payload();
});
```

`RedisMessage.pattern()` será `null` en suscripciones exactas.

`subscribe(...)` registra el handler localmente y solicita la suscripción async a Redis. Si la suscripción async falla, CraftKit cierra esa `RedisSubscription` internamente.

## Suscribirse por patrón

```java
RedisSubscription subscription = redis.subscriber().subscribePattern(
    "hera:prod:events:party:*",
    message -> {
        String pattern = message.pattern();
        String channel = message.channel();
        String payload = message.payload();
    }
);
```

Los patterns permiten `*`, pero no espacios. La longitud máxima sigue siendo 256 caracteres.

`subscribePattern(...)` sigue el mismo comportamiento: registra el handler y solicita `PSUBSCRIBE` de forma async.

## Cerrar suscripciones

```java
subscription.close();
```

`close()` es idempotente. Si hay varios handlers registrados para el mismo channel/pattern, CraftKit solo envía `unsubscribe`/`punsubscribe` cuando se cierra el último handler de ese topic.

## Seguridad del handler

`PubSubSubscriptions` captura `RuntimeException` de handlers. Si un handler falla, no rompe el listener global ni evita que otros handlers reciban el mensaje.

## Conexión Pub/Sub lazy

La conexión Pub/Sub se abre en el primer subscribe. Se mantiene separada de la conexión de comandos.

## Threading y Paper

Los handlers de Pub/Sub corren desde callbacks async de Lettuce. No tocar Paper/Bukkit API directamente.

Ejemplo correcto:

```java
redis.subscriber().subscribe(channel, message -> {
    String payload = message.payload();

    Bukkit.getScheduler().runTask(plugin, () -> {
        // tocar Paper API aquí
    });
});
```

Para Folia, usar el scheduler apropiado del plugin consumidor.
