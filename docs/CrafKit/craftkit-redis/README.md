# `craftkit-redis`

`craftkit-redis` es el módulo de CraftKit que entrega una integración Redis estándar para plugins de HERA: conexión Lettuce configurada, operaciones async, caché con TTL, estado rápido, Pub/Sub entre servidores, leases ligeros para coordinación y helpers de nombres para keys/channels.

El módulo es **Paper-free**: no depende de Paper/Bukkit y no agenda callbacks al hilo principal. Los plugins consumidores deben volver al scheduler correcto antes de tocar APIs de Paper.

## Qué resuelve

- Crea y administra internamente un cliente Lettuce `7.6.0.RELEASE`.
- Expone una API pública basada en `CompletableFuture`, sin comandos Redis síncronos públicos.
- Mantiene Lettuce como detalle interno: no hay raw Lettuce público en v1.
- Aplica defaults seguros para timeouts, reconexión, colas y threads.
- Obliga a usar TTL en escrituras de caché temporales.
- Estandariza nombres de keys y channels con `keyPrefix`, `environment` y `serverId`.
- Separa conexión de comandos y conexión Pub/Sub.
- Proporciona Pub/Sub simple para comunicación rápida no durable.
- Proporciona leases seguros por token con release Lua y `withLease(...)`.
- Obliga al consumidor a cerrar explícitamente `RedisClient` en shutdown.

## Dependencias del módulo

`craftkit-redis/build.gradle.kts` declara:

```kotlin
implementation(libs.lettuce.core)
```

Versión actual en `gradle/libs.versions.toml`:

| Librería | Versión |
| --- | --- |
| Lettuce Core | `7.6.0.RELEASE` |

La dependencia está como `implementation` porque la API pública de CraftKit no expone tipos Lettuce.

## Documentos de esta sección

1. [Inicio rápido](./inicio-rapido.md)
2. [Arquitectura y componentes](./arquitectura.md)
3. [Configuración](./configuracion.md)
4. [Caché y estado rápido](./cache-estado.md)
5. [Pub/Sub entre servidores](./pubsub.md)
6. [Coordinación con leases](./coordinacion-leases.md)
7. [Lifecycle, errores y límites](./lifecycle-errores-limites.md)
8. [Referencia de API pública](./referencia-api.md)

## Regla mental rápida

El plugin consumidor define:

- configuración real de Redis;
- formato de payloads y serialización JSON si la necesita;
- cuándo volver al hilo principal de Paper;
- lógica de negocio;
- qué datos son cache, estado temporal, evento o coordinación.

CraftKit proporciona:

- conexión Redis;
- recursos Lettuce internos;
- API async;
- helpers de keys/channels;
- Pub/Sub;
- leases ligeros;
- cierre y errores base.
