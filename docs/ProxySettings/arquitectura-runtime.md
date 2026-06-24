# Arquitectura y Runtime

ProxySettings se inicializa como plugin Velocity y registra una API Java en memoria para consumidores del mismo proxy.

## Bootstrap

Entrada real:

```text
src/main/java/com/stephanofer/proxysettings/ProxySettingsPlugin.java
```

Durante `ProxyInitializeEvent` hace lo siguiente:

1. Carga `config.yml`.
2. Carga `assets/countries.yml`.
3. Carga `styles/nick-patterns.yml`.
4. Carga `styles/chat-patterns.yml`.
5. Crea `Database` con `Databases.mysql(config.database())`.
6. Crea `SqlPlayerSettingsRepository`.
7. Crea `LanguageResolver`.
8. Crea `CountryFlagService`.
9. Crea `PlayerStyleService`.
10. Crea `DefaultProxySettingsApi`.
11. Registra la API en `ProxySettingsProvider`.
12. Registra `VelocityConnectionListener` en el event manager.

Si algo falla durante el startup, el plugin loguea:

```text
Could not enable ProxySettings
```

y cierra la base de datos si fue creada.

## Shutdown

Durante `ProxyShutdownEvent`:

1. Desregistra `ProxySettingsProvider` si la API actual coincide.
2. Invalida toda la cache local.
3. Cierra `Database`.

El cierre de DB delega en `craftkit-database`.

## Lifecycle de jugador

Listener real:

```text
src/main/java/com/stephanofer/proxysettings/platform/velocity/VelocityConnectionListener.java
```

### `PostLoginEvent`

Cuando un jugador entra al proxy:

```java
api.refresh(playerId)
```

`refresh` siempre consulta DB y reemplaza el snapshot en cache.

Si la consulta falla, se loguea warning y el jugador puede seguir usando defaults cuando un consumidor pida datos.

### `DisconnectEvent`

Cuando un jugador sale:

```java
api.invalidate(playerId)
```

solo si `settings.cache-cleanup-on-disconnect` está en `true`.

## Cache por sesión

La cache real está en `DefaultProxySettingsApi`:

```java
Caffeine.newBuilder().maximumSize(cacheMaximumSize).build();
```

No hay TTL. Esto es una decisión de diseño explícita.

Comportamiento actual:

- al entrar, `refresh` carga desde DB;
- durante la sesión, los consumidores leen cache;
- al salir, la cache se limpia si la config lo permite;
- los cambios hechos en Paper se reflejan al reconectar al proxy.

## Por qué no hay TTL

TTL daría una actualización eventual no determinística. El diseño actual prefiere comportamiento claro: si un jugador cambia idioma, bandera o style del lado Paper, Velocity lo ve después de reconectar.

## Futuro Redis

El código deja documentado el camino futuro:

```java
// Future upgrade path: Paper-side NetworkPlayerSettings can publish setting-change events
// through Redis. When that exists, invalidate and reload the affected snapshot here without
// requiring a proxy reconnect. For now, Velocity settings are session-scoped and refresh on reconnect.
```

Esta versión no implementa Redis.

## Catálogos en memoria

Los catálogos se cargan al startup y quedan como estructuras inmutables:

- `CountryAssetCatalog`;
- `StylePatternCatalog` para nick;
- `StylePatternCatalog` para chat.

Si un catálogo es inválido, el startup falla. No hay hot reload implementado.

## Dependencia con NetworkPlayerSettings

ProxySettings depende conceptualmente de `NetworkPlayerSettings`, pero no lo carga como plugin Java.

La integración ocurre por:

- misma base de datos;
- misma tabla `${tablePrefix}player_settings`;
- mismos nombres de settings;
- catálogos YAML equivalentes.

ProxySettings no usa Bukkit Services, PlaceholderAPI ni eventos Paper.
