# Lifecycle, errores y límites

## Ownership de recursos

### `Databases.mysql(config)`

CraftKit crea y posee:

- `HikariDataSource`;
- executor interno.

`database.close()` cierra ambos.

### `Databases.mysql(config, executor)`

CraftKit crea y posee:

- `HikariDataSource`.

El consumidor posee:

- executor externo.

`database.close()` no cierra el executor externo.

## Cierre

```java
database.close();
```

`close()`:

1. Marca la instancia como cerrada con `AtomicBoolean`.
2. Cierra el datasource.
3. Si hay executor interno, llama `shutdown()` y espera `shutdownTimeoutMillis`.
4. Si no termina dentro del timeout, llama `shutdownNow()`.
5. Si el cierre falla, lanza `DatabaseException`.

El cierre es idempotente.

## Operaciones después de `close()`

Después de cerrar:

- `query(...)`, `update(...)`, `execute(...)`, `transaction(...)` devuelven `CompletableFuture` fallido.
- `dataSource()` y `table(...)` lanzan `DatabaseException`.
- `isClosed()` devuelve `true`.

## Errores

El módulo usa `DatabaseException` como excepción runtime pública.

Ejemplos de errores envueltos:

- fallo creando datasource;
- fallo en query;
- fallo en update;
- fallo en operación;
- fallo en transacción;
- fallo en Flyway;
- uso después de cerrar;
- configuración inválida;
- prefijo o nombre de tabla inválido.

## Secretos

`DatabaseConfig.toString()` no expone la contraseña. La renderiza como:

```text
password=<hidden>
```

El mensaje al fallar la creación del datasource usa host, puerto y database, pero no incluye password.

## Límites actuales

`craftkit-database` no implementa:

- Paper scheduler;
- retorno automático al main thread;
- ORM;
- query builder;
- repositorios genéricos;
- transaction manager avanzado;
- nested transactions;
- helpers de savepoints;
- health checks;
- métricas;
- integración con Testcontainers o MySQL real en tests.

Estos límites son intencionales para mantener el módulo ligero y enfocado en infraestructura crítica.

## Verificación existente

La suite de tests cubre:

- defaults y validación de configs;
- ocultamiento de password;
- construcción de `HikariConfig`;
- validación de prefijos/tablas;
- executor interno y ownership;
- cierre idempotente;
- operaciones después de close;
- migraciones disabled/enabled;
- configuración Flyway con placeholders/history table;
- estrategias `FAIL`, `BASELINE_AT_ZERO`, `BASELINE_AT_VERSION`;
- transacciones con commit/rollback/restauración de estado;
- executor de transacciones.
