# `craftkit-database`

`craftkit-database` es el módulo de CraftKit que entrega infraestructura MySQL lista para plugins de HERA: pool HikariCP, migraciones Flyway, ejecución async de operaciones JDBC, transacciones, validación de nombres de tablas y lifecycle explícito.

El módulo es **Paper-free**: no depende de Paper/Bukkit y no agenda callbacks al main thread. Los plugins consumidores deben volver al hilo principal por su cuenta antes de tocar APIs de Paper.

## Qué resuelve

- Crea un `HikariDataSource` con defaults consistentes.
- Ejecuta queries, updates, operaciones y migraciones en un executor dedicado.
- Evita usar `ForkJoinPool.commonPool()` o `CompletableFuture.supplyAsync(...)` sin executor explícito.
- Integra Flyway `12.8.1` con placeholders y tabla de historial prefijada.
- Soporta bases compartidas con tablas de otros plugins mediante `ExistingSchemaStrategy.BASELINE_AT_ZERO`.
- Proporciona transacciones con una sola conexión, `commit`, `rollback`, isolation level opcional y restauración del estado de la conexión.
- Obliga al consumidor a cerrar explícitamente `Database` en shutdown.

## Dependencias del módulo

`craftkit-database/build.gradle.kts` declara:

```kotlin
implementation(libs.flyway.core)
implementation(libs.flyway.mysql)
implementation(libs.hikari)
runtimeOnly(libs.mysql.connector)
```

Versiones actuales en `gradle/libs.versions.toml`:

| Librería | Versión |
| --- | --- |
| HikariCP | `7.0.2` |
| MySQL Connector/J | `9.7.0` |
| Flyway | `12.8.1` |

## Documentos de esta sección

1. [Inicio rápido](./inicio-rapido.md)
2. [Arquitectura y componentes](./arquitectura.md)
3. [Configuración](./configuracion.md)
4. [Migraciones con Flyway](./migraciones-flyway.md)
5. [Operaciones SQL async](./operaciones-sql.md)
6. [Transacciones](./transacciones.md)
7. [Lifecycle, errores y límites](./lifecycle-errores-limites.md)
8. [Referencia de API pública](./referencia-api.md)

## Regla mental rápida

El plugin consumidor define:

- tablas;
- migraciones SQL;
- modelos;
- repositorios;
- queries;
- índices;
- cache.

CraftKit proporciona:

- conexión;
- pool;
- executor DB;
- migraciones Flyway;
- validaciones;
- transacciones;
- cierre y errores base.
