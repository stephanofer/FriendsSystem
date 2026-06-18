# Operaciones SQL async

`Database` ofrece tres operaciones JDBC async para uso normal:

```java
<T> CompletableFuture<T> query(SqlQuery<T> query);
CompletableFuture<Integer> update(SqlUpdate update);
CompletableFuture<Void> execute(SqlOperation operation);
```

Todas corren en el executor DB configurado y usan una conexión obtenida desde Hikari con try-with-resources.

## `query(...)`

Para lecturas que devuelven un valor.

```java
CompletableFuture<Optional<PlayerData>> future = database.query(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT coins FROM " + database.table("players") + " WHERE uuid = ?"
    )) {
        statement.setString(1, uuid.toString());

        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }

            return Optional.of(new PlayerData(uuid, result.getInt("coins")));
        }
    }
});
```

## `update(...)`

Para operaciones que devuelven cantidad de filas afectadas.

```java
CompletableFuture<Integer> future = database.update(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(
        "UPDATE " + database.table("players") + " SET coins = ? WHERE uuid = ?"
    )) {
        statement.setInt(1, coins);
        statement.setString(2, uuid.toString());
        return statement.executeUpdate();
    }
});
```

## `execute(...)`

Para operaciones sin resultado.

```java
CompletableFuture<Void> future = database.execute(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(
        "DELETE FROM " + database.table("sessions") + " WHERE expires_at < NOW()"
    )) {
        statement.executeUpdate();
    }
});
```

## `database.table(...)`

`database.table("players")` compone el prefijo configurado con el nombre de tabla.

Con `tablePrefix("survival_")`:

```java
database.table("players") // survival_players
```

Validaciones:

- prefijo: `[A-Za-z0-9_]*`, máximo `48` caracteres;
- nombre de tabla: `[A-Za-z0-9_]+`, máximo `48` caracteres;
- nombre compuesto: máximo `64` caracteres;
- se rechazan espacios, puntos, backticks, guiones y nombre vacío.

## `DataSource` como escape hatch

```java
DataSource dataSource = database.dataSource();
```

Existe para integraciones avanzadas. Si el consumidor usa `DataSource` directo, también asume la responsabilidad de no bloquear el main thread y de cerrar `Connection`, `PreparedStatement` y `ResultSet` correctamente.

`dataSource()` falla con `DatabaseException` si la base ya está cerrada.

## Reglas de uso

### Hacer

- Usar `PreparedStatement`.
- Usar `database.table(...)` para tablas propias.
- Manejar errores del `CompletableFuture`.
- Volver al main thread antes de tocar Paper API.

### No hacer

- No construir SQL con input de jugadores.
- No llamar `.join()` en eventos, comandos o gameplay frecuente.
- No tocar Paper API dentro del callback async.
- No llamar `query/update/execute` después de `database.close()`.

Después de `close()`, `query`, `update` y `execute` devuelven un `CompletableFuture` fallido con `DatabaseException`.
