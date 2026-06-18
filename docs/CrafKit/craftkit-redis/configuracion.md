# Configuración — `craftkit-redis`

`RedisConfig` concentra la configuración de conexión, timeouts, reconexión, nombres y recursos internos.

## Crear configuración

```java
RedisConfig config = RedisConfig.builder()
    .host("127.0.0.1")
    .port(6379)
    .database(0)
    .username("default")
    .password("secret")
    .ssl(false)
    .verifyPeer(true)
    .keyPrefix("hera")
    .environment("prod")
    .serverId("lobby-1")
    .build();
```

## Defaults actuales

| Campo | Default | Validación |
| --- | ---: | --- |
| `host` | `localhost` | no vacío |
| `port` | `6379` | `1..65535` |
| `database` | `0` | `>= 0` |
| `username` | `""` | no `null`; se aplica `trim()` |
| `password` | `""` | no `null` |
| `ssl` | `false` | boolean |
| `verifyPeer` | `true` | boolean |
| `commandTimeout` | `3s` | positivo |
| `connectTimeout` | `3s` | positivo |
| `shutdownTimeout` | `10s` | positivo |
| `autoReconnect` | `true` | boolean |
| `pingBeforeActivate` | `true` | boolean |
| `requestQueueSize` | `10_000` | `1..100_000` |
| `reconnectMinDelay` | `100ms` | positivo |
| `reconnectMaxDelay` | `10s` | positivo y `>= reconnectMinDelay` |
| `ioThreads` | `2` | `>= 1` |
| `computationThreads` | `2` | `>= 1` |
| `keyPrefix` | `hera` | componente válido |
| `environment` | `default` | componente válido |
| `serverId` | `unknown` | componente válido |

## Identidad de entorno

Estos campos afectan keys, channels y leases:

```java
.keyPrefix("hera")
.environment("prod")
.serverId("lobby-1")
```

`keyPrefix` y `environment` forman parte de keys/channels:

```text
hera:prod:player:session:<uuid>
hera:prod:events:party:member-joined
```

`serverId` se usa internamente para prefijar tokens de lease:

```text
lobby-1:<token-aleatorio>
```

## Validación de componentes

`keyPrefix`, `environment`, `serverId`, keys y channels aceptan componentes con:

```text
[a-zA-Z0-9._:-]+
```

No se aceptan componentes vacíos, espacios ni componentes mayores a 256 caracteres.

## Autenticación y TLS

La configuración soporta tres escenarios:

### Red privada sin TLS

```java
RedisConfig.builder()
    .host("10.0.0.10")
    .password("secret")
    .ssl(false)
    .build();
```

### ACL con username/password

```java
RedisConfig.builder()
    .username("hera_plugins")
    .password("secret")
    .build();
```

### TLS

```java
RedisConfig.builder()
    .ssl(true)
    .verifyPeer(true)
    .build();
```

## Redacción de secretos

`RedisConfig.toString()` nunca imprime la contraseña real. Siempre muestra:

```text
password=<hidden>
```

## Queue size

`requestQueueSize` controla cuántos comandos puede encolar Lettuce durante desconexiones o backpressure.

CraftKit no permite valores mayores a `RedisConfig.MAX_REQUEST_QUEUE_SIZE`, actualmente `100_000`, para evitar colas efectivamente ilimitadas.

Recomendación: mantener el default `10_000` salvo que haya evidencia operativa para cambiarlo.
