# Arquitectura — `craftkit-database`

`craftkit-database` expone una API pequeña y deja la implementación concreta en clases internas.

## Mapa de componentes

| Componente | Tipo | Responsabilidad |
| --- | --- | --- |
| `Databases` | Público | Entry point para crear `Database`. |
| `Database` | Público | Contrato principal: migraciones, operaciones async, transacciones, `DataSource`, tablas y cierre. |
| `DatabaseConfig` | Público | Configuración raíz de MySQL, pool, executor, migraciones y propiedades JDBC. |
| `PoolConfig` | Público | Parámetros HikariCP. |
| `ExecutorConfig` | Público | Parámetros del executor interno de DB. |
| `MigrationConfig` | Público | Parámetros de Flyway y estrategia para schemas existentes. |
| `TransactionOptions` | Público | Opciones por transacción: isolation y read-only. |
| `TransactionIsolation` | Público | Mapeo a niveles JDBC de isolation. |
| `ExistingSchemaStrategy` | Público | Estrategia de Flyway para bases no vacías/schemas existentes. |
| `SqlQuery`, `SqlUpdate`, `SqlOperation`, `SqlTransaction` | Público | Functional interfaces usadas por el consumidor. |
| `DatabaseException` | Público | Runtime exception del módulo. |
| `HikariDatabase` | Interno | Implementación de `Database`. |
| `HikariDataSources` | Interno | Construcción de `HikariConfig` y `HikariDataSource`. |
| `DatabaseExecutors` | Interno | Creación y cierre del executor interno. |
| `FlywayMigrator` | Interno | Configuración y ejecución de Flyway. |
| `DatabaseMigrator` | Interno | Abstracción interna para migraciones. |
| `TablePrefixes` | Interno | Validación y composición de prefijos/nombres de tabla. |

## Flujo de creación

```java
Database database = Databases.mysql(config);
```

Internamente:

1. Valida `DatabaseConfig`.
2. Crea un `ExecutorService` con `DatabaseExecutors.createExecutor(config.executor())`.
3. Crea un `HikariDataSource` con `HikariDataSources.create(config)`.
4. Crea `FlywayMigrator` usando el datasource, `MigrationConfig`, `tablePrefix` y el `ClassLoader` configurado para resolver migraciones `classpath:`.
5. Devuelve `HikariDatabase`.

Si falla la creación del datasource o de la instancia, el código intenta cerrar los recursos ya creados y agrega fallos de cierre como `suppressed`.

## Variante con executor externo

```java
Database database = Databases.mysql(config, customExecutor);
```

En esta variante CraftKit crea y cierra el `HikariDataSource`, pero **no cierra** el executor externo. El consumidor es dueño de ese executor.

## Modelo async

Todas las operaciones principales se envían a un executor explícito:

- `migrate()`
- `query(...)`
- `update(...)`
- `execute(...)`
- `transaction(...)`

`HikariDatabase` usa `executor.execute(...)` y completa manualmente un `CompletableFuture`. El código actual no usa `ForkJoinPool.commonPool()` ni `CompletableFuture.supplyAsync(...)`.

## Relación con Paper

El módulo no importa ni usa Paper/Bukkit. Esto mantiene `craftkit-database` como infraestructura Java/JDBC reusable.

Consecuencia: cualquier callback que toque jugadores, mundos, inventarios, scoreboards o cualquier API Paper debe volver al main thread desde el plugin consumidor.
