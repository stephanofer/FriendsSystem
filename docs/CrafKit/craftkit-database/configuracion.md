# Configuración — `craftkit-database`

La configuración se construye con builders inmutables. Si una configuración inválida llega a `build()`, se lanza `DatabaseException`.

## `DatabaseConfig`

Config raíz para crear una conexión MySQL.

```java
MigrationConfig migration = MigrationConfig.builder()
    .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
    .classLoader(getClass().getClassLoader())
    .build();

DatabaseConfig config = DatabaseConfig.builder()
    .host("127.0.0.1")
    .port(3306)
    .database("hera_network")
    .username("survival")
    .password("secret")
    .tablePrefix("survival_")
    .pool(PoolConfig.builder().maximumPoolSize(8).build())
    .executor(ExecutorConfig.builder().threadNamePrefix("survival-db"))
    .migration(migration)
    .putJdbcProperty("socketTimeout", "4000")
    .build();
```

### Campos

| Campo | Default | Validación / comportamiento |
| --- | --- | --- |
| `host` | requerido | No puede estar vacío. |
| `port` | `3306` | Debe estar entre `1` y `65535`. |
| `database` | requerido | No puede estar vacío. |
| `username` | requerido | No puede estar vacío. |
| `password` | `""` | No puede ser `null`. |
| `tablePrefix` | `""` | Solo letras, números y `_`; se valida también contra la tabla de historial Flyway. |
| `pool` | `PoolConfig.builder().build()` | Config Hikari. |
| `executor` | derivado de `PoolConfig` | Si no se define, usa `maximumPoolSize` como cantidad de threads. |
| `migration` | `MigrationConfig.builder().build()` | Config Flyway. |
| `jdbcProperties` | vacío | Keys no vacías; values no `null`. |

`DatabaseConfig.toString()` oculta la contraseña como `password=<hidden>`.

> Nota: `MigrationConfig.sharedDatabaseDefaults()` devuelve una configuración final. Si el plugin consumidor necesita migraciones `classpath:` desde su propio JAR, construya el `MigrationConfig` con `classLoader(getClass().getClassLoader())` y aplique también `existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)` cuando use base compartida.

## `PoolConfig`

Configura HikariCP.

| Campo | Default | Validación |
| --- | --- | --- |
| `poolName` | `craftkit-mysql` | No puede estar vacío. |
| `maximumPoolSize` | `10` | Debe ser `>= 1`. |
| `minimumIdle` | `null` | Si existe, debe estar entre `0` y `maximumPoolSize`. |
| `connectionTimeoutMillis` | `10_000` | Debe ser `> 0`. |
| `validationTimeoutMillis` | `5_000` | Debe ser `> 0`. |
| `idleTimeoutMillis` | `600_000` | Debe ser `>= 0`. |
| `maxLifetimeMillis` | `1_800_000` | Debe ser `>= 0`. |
| `autoCommit` | `true` | Booleano. |
| `leakDetectionThresholdMillis` | `0` | Debe ser `>= 0`. |

## `ExecutorConfig`

Configura el executor interno cuando se usa `Databases.mysql(config)`.

| Campo | Default | Validación |
| --- | --- | --- |
| `threadCount` | `PoolConfig.maximumPoolSize` cuando se construye desde `DatabaseConfig` | Debe ser `>= 1`. |
| `threadNamePrefix` | `craftkit-database` | No puede estar vacío. |
| `daemon` | `true` | Booleano. |
| `shutdownTimeoutMillis` | `10_000` | Debe ser `> 0`. |

El executor interno es un `ThreadPoolExecutor` fijo con cola `ArrayBlockingQueue`. La capacidad de cola interna es `max(64, threadCount * 128)`.

## Propiedades JDBC por defecto

`HikariDataSources` agrega estas propiedades por defecto:

| Propiedad | Valor |
| --- | --- |
| `cachePrepStmts` | `true` |
| `prepStmtCacheSize` | `250` |
| `prepStmtCacheSqlLimit` | `2048` |
| `useServerPrepStmts` | `true` |
| `rewriteBatchedStatements` | `true` |

El consumidor puede sobreescribirlas con `putJdbcProperty(...)` o `jdbcProperties(...)`.

## Comportamiento de creación del datasource

La URL JDBC construida es:

```text
jdbc:mysql://<host>:<port>/<database>
```

El `HikariConfig` actual usa `initializationFailTimeout = -1`. Esto permite crear el datasource sin exigir una conexión inmediata; errores de conectividad pueden aparecer al ejecutar operaciones o migraciones.
