# Transacciones

Las transacciones permiten ejecutar varias operaciones SQL como una sola unidad atómica.

```text
éxito -> commit
fallo -> rollback
```

Esto es crítico para economía, compras, rewards, transferencias, inventarios persistentes y cualquier flujo donde varias escrituras deben quedar consistentes.

## API

```java
<T> CompletableFuture<T> transaction(SqlTransaction<T> transaction);

<T> CompletableFuture<T> transaction(
    TransactionOptions options,
    SqlTransaction<T> transaction
);
```

`SqlTransaction<T>` recibe una `Connection` y devuelve un resultado.

```java
@FunctionalInterface
public interface SqlTransaction<T> {
    T execute(Connection connection) throws SQLException;
}
```

## Ejemplo: transferencia de coins

```java
CompletableFuture<Void> future = database.transaction(connection -> {
    withdrawCoins(connection, fromUuid, 100);
    depositCoins(connection, toUuid, 100);
    return null;
});
```

Las dos operaciones usan la misma conexión. Si una falla, CraftKit intenta `rollback()`.

## Ejemplo con resultado

```java
CompletableFuture<Boolean> purchase = database.transaction(connection -> {
    int coins = loadCoins(connection, uuid);

    if (coins < price) {
        return false;
    }

    updateCoins(connection, uuid, coins - price);
    insertPurchase(connection, uuid, itemId);
    return true;
});
```

## Isolation y read-only

`TransactionOptions` permite configurar isolation/read-only por transacción.

```java
database.transaction(
    TransactionOptions.builder()
        .isolation(TransactionIsolation.READ_COMMITTED)
        .readOnly(false)
        .build(),
    connection -> {
        // SQL transaccional
        return result;
    }
);
```

Factories disponibles:

```java
TransactionOptions.defaults();
TransactionOptions.readUncommitted();
TransactionOptions.readCommitted();
TransactionOptions.repeatableRead();
TransactionOptions.serializable();
TransactionOptions.readOnly(TransactionIsolation.READ_COMMITTED);
```

## `TransactionIsolation`

| Valor | Comportamiento |
| --- | --- |
| `DEFAULT` | No cambia el isolation de la conexión. |
| `READ_UNCOMMITTED` | Mapea a `Connection.TRANSACTION_READ_UNCOMMITTED`. |
| `READ_COMMITTED` | Mapea a `Connection.TRANSACTION_READ_COMMITTED`. |
| `REPEATABLE_READ` | Mapea a `Connection.TRANSACTION_REPEATABLE_READ`. |
| `SERIALIZABLE` | Mapea a `Connection.TRANSACTION_SERIALIZABLE`. |

Default actual:

```text
TransactionIsolation.DEFAULT
readOnly = false
```

## Qué hace CraftKit internamente

`HikariDatabase.transaction(...)`:

1. Agenda la transacción en el executor DB.
2. Obtiene una conexión de Hikari.
3. Guarda `autoCommit`, `readOnly` e isolation anteriores.
4. Aplica isolation si no es `DEFAULT`.
5. Aplica `readOnly` si difiere del estado actual.
6. Ejecuta `connection.setAutoCommit(false)`.
7. Ejecuta el callback del consumidor.
8. Hace `commit()` si todo sale bien.
9. Hace `rollback()` si falla el callback o el commit.
10. Restaura `autoCommit`, `readOnly` e isolation.
11. Cierra la conexión, devolviéndola al pool.

Si rollback o restauración fallan, esos errores se agregan como `suppressed` al error principal cuando corresponde.

## Regla más importante

Dentro de una transacción, usar siempre la `Connection` recibida.

Correcto:

```java
database.transaction(connection -> {
    updateA(connection);
    updateB(connection);
    return null;
});
```

Incorrecto:

```java
database.transaction(connection -> {
    database.update(otherConnection -> updateA(otherConnection)); // fuera de la transacción
    return null;
});
```

`database.update(...)`, `database.query(...)` y `database.execute(...)` obtienen otra conexión del pool. Si se llaman dentro de `transaction`, quedan fuera de la transacción actual.

## Qué no incluye todavía

La implementación actual no agrega helpers de:

- nested transactions;
- savepoints;
- timeout propio de transacción;
- transaction manager;
- ORM o query builder.

Si se necesitan savepoints, el consumidor puede usar JDBC directo dentro de la misma conexión recibida.
