# Coordinación con leases — `craftkit-redis`

`RedisCoordinator` permite coordinación ligera entre servidores. Está pensado para tareas cortas donde solo una instancia debe ejecutar una acción durante un período limitado.

## Qué es un lease

Un lease es un permiso temporal guardado en Redis:

```text
SET key token NX PX ttl
```

Significa:

- crear la key solo si no existe (`NX`);
- guardar un token único;
- expirar automáticamente después del TTL (`PX`).

Si el servidor que obtuvo el lease crashea, Redis liberará la key cuando expire el TTL.

## API pública

```java
public interface RedisCoordinator {
    CompletableFuture<Optional<RedisLease>> tryAcquireLease(String key, Duration ttl);

    <T> CompletableFuture<Optional<T>> withLease(
        String key,
        Duration ttl,
        Supplier<CompletableFuture<T>> task
    );
}
```

`RedisLease` no extiende `AutoCloseable` porque liberar el lease es async:

```java
public interface RedisLease {
    String key();
    String token();
    Duration ttl();
    CompletableFuture<Boolean> release();
}
```

## `tryAcquireLease(...)`

```java
redis.coordinator().tryAcquireLease(
    redis.key("matchmaking", "processor", "solo"),
    Duration.ofSeconds(10)
).thenAccept(lease -> {
    if (lease.isEmpty()) {
        // Otro servidor tiene el lease.
        return;
    }

    RedisLease acquired = lease.get();
    // Ejecutar trabajo y luego liberar explícitamente.
    acquired.release();
});
```

El token incluye `serverId`:

```text
<serverId>:<token-aleatorio>
```

## Release seguro por Lua

El release no usa `DEL` directo. Usa Lua para borrar solo si el token coincide:

```lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
else
  return 0
end
```

Esto evita borrar accidentalmente un lease adquirido por otro servidor después de que el primer lease expiró.

## `withLease(...)`

`withLease(...)` es la forma recomendada porque libera automáticamente el lease al finalizar la tarea async.

```java
redis.coordinator().withLease(
    redis.key("matchmaking", "processor", "solo"),
    Duration.ofSeconds(10),
    () -> processQueueAsync()
).thenAccept(result -> {
    if (result.isEmpty()) {
        // No se obtuvo el lease.
        return;
    }

    // La tarea se ejecutó y el lease fue liberado.
});
```

Comportamiento real:

- si no obtiene el lease, devuelve `Optional.empty()` y no ejecuta `task`;
- si obtiene el lease, ejecuta `task`;
- si `task` completa correctamente, libera el lease y devuelve `Optional.ofNullable(resultado)`;
- si `task` lanza antes de devolver `CompletableFuture`, libera el lease y propaga el fallo;
- si el future de `task` falla, libera el lease y propaga el fallo;
- si `task` falla y también falla el release, el fallo de release se agrega como `suppressed`;
- si `task` fue exitoso pero falla el release, el future completa excepcionalmente con `RedisException`.

Como el resultado usa `Optional.ofNullable(...)`, una tarea que devuelve `null` produce `Optional.empty()`. Evitar devolver `null` si el consumidor necesita distinguir entre “no se obtuvo el lease” y “la tarea devolvió resultado vacío”.

## Límites

No hay auto-renewal en v1. El TTL debe cubrir la duración máxima esperada de la tarea más margen.

Ejemplo malo:

```text
TTL: 5s
Tarea: 30s
```

En ese caso el lease puede expirar mientras la tarea sigue corriendo y otro servidor podría tomarlo.

## Cuándo usar leases

Usar para coordinación corta:

- elegir un servidor que procesa una cola temporal;
- evitar doble ejecución de una tarea rápida;
- reservar un recurso durante pocos segundos;
- coordinar matchmaking ligero.

No usar como consenso distribuido fuerte ni como reemplazo de transacciones durables.
