# Migraciones con Flyway

`craftkit-database` integra Flyway para que cada plugin consumidor pueda versionar su propio schema SQL.

## Ubicación por defecto

`MigrationConfig.DEFAULT_LOCATION` es:

```text
classpath:db/migration
```

En un plugin consumidor, normalmente corresponde a:

```text
src/main/resources/db/migration/
```

Ejemplo:

```text
src/main/resources/db/migration/V1__create_players.sql
src/main/resources/db/migration/V2__add_player_indexes.sql
```

## Ejecutar migraciones

```java
database.migrate().join();
```

`migrate()` se ejecuta async en el executor DB. En startup es común usar `join()` para esperar a que las tablas estén listas antes de activar repositorios y features.

Si se construye con `MigrationConfig.builder().enabled(false).build()`, `migrate()` devuelve un `CompletableFuture` ya completado y no ejecuta Flyway.

## Placeholders

`FlywayMigrator` combina los placeholders del consumidor con uno automático:

| Placeholder | Valor |
| --- | --- |
| `tablePrefix` | `DatabaseConfig.tablePrefix()` |

Ejemplo de migración:

```sql
CREATE TABLE `${tablePrefix}players` (
    uuid CHAR(36) NOT NULL PRIMARY KEY,
    coins INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Con `tablePrefix("survival_")`, Flyway genera:

```sql
CREATE TABLE `survival_players` (...);
```

## Tabla de historial Flyway

CraftKit configura la tabla de historial con el mismo prefijo:

```text
<tablePrefix>flyway_schema_history
```

Ejemplo:

```text
survival_flyway_schema_history
```

Esto permite que varios plugins compartan una misma base de datos sin usar la misma tabla de historial.

## `MigrationConfig`

| Campo | Default | Descripción |
| --- | --- | --- |
| `enabled` | `true` | Activa o desactiva Flyway. |
| `locations` | `[classpath:db/migration]` | Locations de migraciones. |
| `baselineOnMigrate` | `false` | Flag directo de Flyway. También se activa si `existingSchemaStrategy != FAIL`. |
| `validateOnMigrate` | `true` | Valida checksums antes de migrar. |
| `cleanDisabled` | `true` | Deshabilita `clean`. Recomendado para producción. |
| `existingSchemaStrategy` | `FAIL` | Estrategia cuando la DB ya tiene tablas y no hay history table para este prefijo. |
| `baselineVersion` | `0` | Versión baseline para `BASELINE_AT_VERSION`; `BASELINE_AT_ZERO` fuerza `0`. |
| `baselineDescription` | `CraftKit baseline` | Descripción baseline de Flyway. |
| `placeholders` | vacío | Placeholders extra del consumidor. |
| `classLoader` | context classloader actual | ClassLoader usado por Flyway para resolver migraciones `classpath:`. En Paper, el plugin consumidor debe pasar su propio classloader si las migraciones viven dentro de su JAR. |

En plugins Paper, configure explícitamente el classloader del plugin consumidor para que Flyway pueda resolver `src/main/resources/db/migration/` desde ese JAR:

```java
MigrationConfig migration = MigrationConfig.builder()
    .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
    .classLoader(getClass().getClassLoader())
    .build();
```

## Estrategias para schemas existentes

### `FAIL`

Default seguro.

```java
MigrationConfig.builder().build();
```

Si la base no está vacía y no existe la tabla de historial Flyway correspondiente, Flyway falla. Es útil para evitar que una configuración equivocada o una DB inesperada se acepte silenciosamente.

### `BASELINE_AT_ZERO`

Recomendado para HERA cuando la base es compartida por varios plugins.

```java
MigrationConfig migration = MigrationConfig.sharedDatabaseDefaults();
```

Equivale a:

```java
MigrationConfig.builder()
    .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
    .build();
```

Uso típico:

```text
Base: hera_network
Tablas existentes: lobby_players, ranks_permissions, punishments_history
Nuevo plugin: survival_
```

Flyway crea `survival_flyway_schema_history` con baseline `0` y luego ejecuta `V1`, `V2`, etc. Esto evita saltarse `V1`.

### `BASELINE_AT_VERSION`

Para adoptar tablas viejas del mismo plugin que ya existen antes de usar Flyway.

```java
MigrationConfig migration = MigrationConfig.builder()
    .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_VERSION)
    .baselineVersion("1")
    .baselineDescription("Existing pre-Flyway schema")
    .build();
```

Este modo marca la versión indicada como ya existente. Debe usarse solo si las tablas actuales realmente equivalen a esa migración.

## Recomendación HERA

Para plugins nuevos en una DB compartida:

```java
.migration(MigrationConfig.sharedDatabaseDefaults())
```

Para plugins nuevos con DB propia vacía, el default `FAIL` también funciona.

Para plugins antiguos con tablas ya existentes del mismo plugin, usar `BASELINE_AT_VERSION` conscientemente.

## Validaciones

`MigrationConfig` valida:

- si migraciones están enabled, `locations` no puede quedar vacío;
- cada location no puede estar vacía;
- placeholder key no puede estar vacía;
- placeholder value no puede ser `null`;
- `existingSchemaStrategy` no puede ser `null`;
- `baselineVersion` no puede estar vacía y solo permite dígitos y puntos;
- `baselineDescription` no puede estar vacía.
