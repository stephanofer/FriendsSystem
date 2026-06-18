# Arquitectura y componentes — `craftkit-redis`

`craftkit-redis` separa la API pública que usan los plugins consumidores de la implementación Lettuce interna.

## Mapa de paquetes

```text
com.hera.craftkit.redis
  RedisClients
  RedisClient
  RedisConfig
  RedisCache
  RedisState
  RedisPublisher
  RedisSubscriber
  RedisSubscription
  RedisMessage
  RedisMessageHandler
  RedisCoordinator
  RedisLease
  RedisException

com.hera.craftkit.redis.internal
  LettuceRedisClient
  RedisCoordinatorImpl
  DefaultRedisLease
  PubSubSubscriptions
  RedisNames
  RedisCommandExecutor
```

## Entrada pública

La entrada del módulo es:

```java
RedisClient redis = RedisClients.lettuce(config);
```

`RedisClients` solo valida que `RedisConfig` no sea `null` y delega la creación a `LettuceRedisClient.create(...)`.

## Cliente principal

`LettuceRedisClient` implementa directamente:

- `RedisClient`
- `RedisCache`
- `RedisState`
- `RedisPublisher`
- `RedisSubscriber`
- `RedisCommandExecutor` interno

Por eso las vistas públicas devuelven el mismo objeto bajo interfaces específicas:

```java
redis.cache();
redis.state();
redis.publisher();
redis.subscriber();
redis.coordinator();
```

## Conexiones Redis

La implementación usa dos conexiones Lettuce separadas.

### Conexión de comandos

Creada al construir el cliente:

```text
StatefulRedisConnection<String, String>
```

Se usa para:

- `ping`
- `get`
- `set`
- `setIfAbsent`
- `expire`
- `delete`
- `unlink`
- `ttl`
- `increment`
- `incrementBy`
- `getAndDelete`
- `publish`
- Lua interno de leases

### Conexión Pub/Sub

Creada de forma lazy cuando se llama a `subscribe(...)` o `subscribePattern(...)`:

```text
StatefulRedisPubSubConnection<String, String>
```

Se usa solo para suscripciones. No se mezcla con comandos normales.

## Recursos Lettuce

`LettuceRedisClient.create(...)` crea `DefaultClientResources` con:

- `ioThreadPoolSize(config.ioThreads())`
- `computationThreadPoolSize(config.computationThreads())`
- delay exponencial de reconexión entre `reconnectMinDelay` y `reconnectMaxDelay`

CraftKit posee estos recursos y los apaga en `RedisClient.close()`.

## Opciones Lettuce aplicadas

`ClientOptions` se configura con:

- `autoReconnect(config.autoReconnect())`
- `pingBeforeActivateConnection(config.pingBeforeActivate())`
- `requestQueueSize(config.requestQueueSize())`
- `SocketOptions.connectTimeout(config.connectTimeout())`

El tamaño de cola está validado por `RedisConfig`: mínimo `1`, máximo `100_000`, default `10_000`.

## Adaptación async

La implementación usa comandos async de Lettuce:

```text
RedisAsyncCommands<String, String>
```

Cada `RedisFuture<T>` se adapta a `CompletableFuture<T>`. Si Lettuce falla, CraftKit completa el future con `RedisException`.

## Sin raw Lettuce público

Ninguna interfaz pública expone tipos Lettuce. Esto mantiene:

- API estable;
- control de lifecycle;
- convenciones de HERA;
- posibilidad futura de cambiar detalles internos.

Si un plugin necesita una operación avanzada no expuesta, la opción correcta en v1 es agregar una API CraftKit específica.

## Sin Paper/Bukkit

El módulo no importa Paper ni Bukkit. Los callbacks async de Redis no deben tocar objetos del servidor directamente. El plugin consumidor debe volver al scheduler de Paper/Folia cuando corresponda.
