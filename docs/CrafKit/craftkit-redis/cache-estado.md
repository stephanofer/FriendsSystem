# Caché y estado rápido — `craftkit-redis`

`RedisCache` y `RedisState` cubren operaciones simples para datos temporales, caché compartida y estado rápido entre servidores.

## `RedisCache`

Acceso:

```java
RedisCache cache = redis.cache();
```

### `get(String key)`

Lee una key.

```java
CompletableFuture<String> future = cache.get(key);
```

Si Redis no tiene la key, Lettuce puede devolver `null`. El consumidor debe manejarlo.

```java
cache.get(key).thenAccept(value -> {
    if (value == null) {
        return;
    }

    // usar value
});
```

### `getMany(Collection<String> keys)`

Lee varias keys en una sola operación Redis usando `MGET`.

```java
CompletableFuture<Map<String, String>> future = cache.getMany(playerStateKeys);
```

Comportamiento:

- si la colección está vacía, devuelve un `Map` vacío y no consulta Redis;
- valida cada key con las mismas reglas de `get(...)`;
- deduplica keys repetidas antes de consultar Redis;
- ejecuta una única operación `MGET`;
- devuelve un `Map` no modificable con el orden de la primera aparición de cada key;
- solo incluye keys encontradas; las keys faltantes no aparecen en el `Map`.

```java
cache.getMany(playerStateKeys).thenAccept(states -> {
    String state = states.get(playerKey);
    boolean missing = !states.containsKey(playerKey);
});
```

No usa `Optional` por key. El consumidor ya conoce la lista original y puede distinguir faltantes con `containsKey(...)` sin agregar más allocations ni complejidad a la API.

### `set(String key, String value, Duration ttl)`

Guarda un valor con TTL obligatorio.

```java
cache.set(key, "online", Duration.ofMinutes(5));
```

Internamente usa `SET` con expiración en milisegundos (`PX`). Devuelve `true` si Redis responde `OK`.

No existe escritura persistente en v1.

### `setIfAbsent(String key, String value, Duration ttl)`

Guarda solo si la key no existe y también requiere TTL.

```java
cache.setIfAbsent(key, "reserved", Duration.ofSeconds(30));
```

Internamente usa `SET NX PX`.

### `expire(String key, Duration ttl)`

Actualiza la expiración de una key.

```java
cache.expire(key, Duration.ofMinutes(2));
```

Internamente usa `PEXPIRE`.

### `delete(String key)`

Borra una key con `DEL`.

```java
cache.delete(key);
```

Devuelve `true` si Redis reporta que borró al menos una key.

### `unlink(String... keys)`

Solicita borrado no bloqueante con `UNLINK`.

```java
cache.unlink(key1, key2, key3);
```

Requiere al menos una key. Devuelve la cantidad reportada por Redis.

### `ttl(String key)`

Lee TTL en milisegundos con `PTTL` y devuelve `Duration`.

```java
cache.ttl(key).thenAccept(ttl -> {
    if (ttl.isZero()) {
        // key faltante o sin expiración según reporte de Redis.
    }
});
```

La API devuelve `Duration.ZERO` cuando Redis reporta key inexistente o key sin expiración.

## `RedisState`

Acceso:

```java
RedisState state = redis.state();
```

### `increment(String key)`

Incrementa un contador en `1`.

```java
state.increment(redis.key("counter", "joins"));
```

### `incrementBy(String key, long amount)`

Incrementa por un valor específico.

```java
state.incrementBy(redis.key("counter", "kills"), 5);
```

### `putIfAbsent(String key, String value, Duration ttl)`

Alias semántico de `setIfAbsent(...)`, pensado para estado rápido.

```java
state.putIfAbsent(key, "processing", Duration.ofSeconds(15));
```

### `getAndDelete(String key)`

Lee y borra atómicamente usando `GETDEL`.

```java
state.getAndDelete(key).thenAccept(value -> {
    // value puede ser null si la key no existía.
});
```

## Validaciones comunes

Todas estas operaciones validan:

- key no `null`;
- key no vacía;
- key sin espacios/caracteres inválidos;
- key máximo 256 caracteres;
- value no `null` cuando aplica;
- TTL positivo cuando aplica.

## Threading

Las operaciones son async. No bloquear el hilo principal con `.join()` o `.get()` dentro de eventos/comandos de Paper.
