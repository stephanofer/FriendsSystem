# Lifecycle, errores y límites — `craftkit-redis`

Esta guía resume cómo se crea, usa y cierra `RedisClient`, qué errores esperar y qué límites tiene v1.

## Creación

```java
RedisClient redis = RedisClients.lettuce(config);
```

Durante la creación, `LettuceRedisClient.create(...)`:

1. crea `DefaultClientResources`;
2. construye `RedisURI`;
3. crea `io.lettuce.core.RedisClient`;
4. aplica `ClientOptions`;
5. abre la conexión principal de comandos con `StringCodec.UTF8`;
6. configura timeout de comandos;
7. crea `RedisCoordinatorImpl`.

Si falla la creación, intenta cerrar los recursos ya creados y lanza `RedisException`.

## Cierre

```java
redis.close();
```

`close()` es idempotente y usa `AtomicBoolean`.

Orden de cierre:

1. cierra todas las subscriptions registradas;
2. cierra conexión Pub/Sub si existe;
3. cierra conexión de comandos;
4. cierra cliente Lettuce;
5. apaga `DefaultClientResources` con `shutdownTimeout`.

Si fallan varios cierres, CraftKit acumula errores como `suppressed` dentro de `RedisException`.

## Uso después de cierre

Después de `close()`:

- `isClosed()` devuelve `true`;
- operaciones de comandos devuelven `CompletableFuture` fallido;
- operaciones inmediatas como `key(...)`, `channel(...)` o `subscribe(...)` lanzan `RedisException`.

## Errores

`RedisException` cubre:

- configuración inválida;
- fallo conectando a Redis;
- fallo ejecutando comandos;
- uso después de cierre;
- fallo de release de lease;
- fallo de cierre de recursos.

Las operaciones async completan excepcionalmente con `RedisException` cuando el fallo ocurre dentro del flujo async.

## Threading

La API pública es async. Los callbacks de `CompletableFuture` pueden correr en threads de Lettuce/Netty o en el hilo que complete el future.

No usar:

```java
redis.cache().get(key).join(); // dentro del main thread de Paper
```

Preferir composición async y volver al scheduler de Paper solo cuando sea necesario.

## Límites de v1

No incluido en v1:

- Redis Sentinel;
- Redis Cluster;
- Redis Streams;
- raw Lettuce público;
- serializers tipados;
- Jackson/Gson/MessagePack interno;
- auto-renewal de leases;
- comandos peligrosos como `KEYS`, `FLUSHDB` o acceso raw a comandos sync.

## Pub/Sub no durable

Pub/Sub no guarda mensajes. Si un servidor está desconectado al momento de publicar, puede perder el evento.

Para eventos críticos, guardar estado en Redis/DB y usar Pub/Sub solo como aviso, o diseñar Streams en una versión futura.

## Serialización

La API usa `String`. CraftKit no serializa objetos ni impone JSON.

El plugin consumidor decide cómo convertir objetos a `String`:

```java
String payload = gson.toJson(event);
redis.publisher().publish(channel, payload);
```

## Nombres y seguridad operacional

Evitar keys manuales cuando sea posible. Preferir:

```java
redis.key("party", "state", partyId);
redis.channel("party", "member-joined");
```

Esto conserva namespacing por `keyPrefix` y `environment`.
