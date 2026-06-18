# Diseño inicial de FriendsSystem

FriendsSystem será un plugin Velocity para amistades, mensajería social y presencia rica de jugadores en la network. La decisión base es mantener MySQL como fuente de verdad, Redis como estado temporal de baja latencia para actividad publicada por Paper, y Caffeine como caché local del proxy.

## Decisiones principales

| Área | Decisión |
| --- | --- |
| Plataforma | Plugin principal en Velocity. |
| Java/Build | Java 25, Gradle actual del proyecto, Shadow con relocation ya configurada. |
| Comandos | `cloud-velocity` con `cloud-minecraft-extras` para Brigadier, errores Adventure y sugerencias ricas. |
| Configuración | `boosted-yaml` con defaults embebidos, `config-version` y auto-update. |
| Persistencia | MySQL vía `craftkit-database` y migraciones Flyway. |
| Estado rápido | Redis obligatorio en producción vía `craftkit-redis` para presencia y actividad rica publicada por Paper. |
| Caché local | Caffeine para perfiles sociales, listas de amigos, settings, idioma, presencia/actividad breve y metadata de LuckPerms. |
| Idioma | Velocity lee el idioma persistido desde la tabla de NetworkPlayerSettings, no desde su API Paper. |
| UX | Adventure MiniMessage, componentes clickeables, hover, actionbar y sonidos con fallback. |
| Permisos | LuckPerms como proveedor de límites, metadata de rango/prefix y permisos de comandos. |

## Alcance V1

- `/friend add <player>` para enviar solicitud.
- `/friend accept <player>` y acciones clickeables en chat.
- `/friend deny <player>` para rechazar solicitud.
- `/friend withdraw <player>` para retirar solicitud enviada.
- `/friend remove <player>` para eliminar amistad.
- `/friend list [page]` con estado online/offline, servidor y actividad resumida.
- `/friend pending` para solicitudes entrantes y salientes.
- `/friend block <player>` y `/friend unblock <player>`.
- `/friend msg <player> <message>` y alias configurable `/fmsg` o `/fm`.
- `/friend reply <message>` y alias configurable `/fr`.
- `/friend broadcast <message>` a amigos online permitidos.
- `/friend toggle <setting>` para visibilidad, mensajes, broadcasts y join/quit.
- Mensajes offline persistidos y entregados al entrar.
- Límites de cantidad de amigos por permisos LuckPerms.
- Integración básica de prefix/rango en listados.
- I18n por jugador en español e inglés.

## Fuera de alcance inicial

| Tema | Motivo |
| --- | --- |
| GUI compleja | En Velocity no hay inventarios; si se requiere menú, debe vivir en Paper o zMenu más adelante. |
| Redis como fuente de verdad | Pub/Sub no es durable; las relaciones sociales deben vivir en MySQL. |
| Actividad rica inferida solo desde Velocity | Velocity sabe servidor actual, pero mapa, modo, partida o teammates deben venir desde plugins Paper. |
| Sincronización instantánea de idioma solo con DB polling | Polling periódico sería ineficiente; se prefiere invalidación por evento o refresh en join. |
| ORM/query builder | JDBC directo con repositorios pequeños es más auditable y rápido para este dominio. |
| `/friend clear` | Se elimina del diseño inicial por ser destructivo y poco necesario para una UX cuidada. |

## Arquitectura

```text
Velocity FriendsSystem
  ├─ Commands: FriendCommand, MessageCommand, AdminCommand
  ├─ Application: FriendService, RequestService, SocialMessageService, PresenceService
  ├─ Integrations: LuckPermsGateway, LanguageSettingsGateway, ActivityChannelGateway
  ├─ Persistence: FriendRepository, RequestRepository, BlockRepository, OfflineMessageRepository
  ├─ Cache: SocialCache, PermissionLimitCache, LanguageCache, PresenceCache
  ├─ Messaging: RedisPresenceStore, RedisActivityStore, PluginMessageListener
  └─ UI: MessageRenderer, MiniMessageTemplates, ClickActions, SoundFeedback
```

### Regla de dependencias

Los comandos solo validan entrada, permisos y delegan. La lógica social vive en servicios. Los repositorios no conocen Velocity, Adventure ni LuckPerms. Los renderers no consultan DB.

## Modelo de datos MySQL

Las tablas usan `${tablePrefix}` de CraftKit y migraciones en `src/main/resources/db/migration/`.

### `friends_profiles`

Guarda datos mínimos de identidad para resolver jugadores offline.

```sql
CREATE TABLE `${tablePrefix}profiles` (
    `player_uuid` BINARY(16) NOT NULL PRIMARY KEY,
    `username` VARCHAR(16) NOT NULL,
    `username_lower` VARCHAR(16) NOT NULL,
    `last_known_prefix` VARCHAR(128) NULL,
    `last_known_primary_group` VARCHAR(64) NULL,
    `last_seen_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_profiles_username_lower` (`username_lower`)
);
```

### `friends_edges`

Relación aceptada. Se guarda una sola fila por par, normalizando `player_low_uuid` y `player_high_uuid` para evitar duplicados.

```sql
CREATE TABLE `${tablePrefix}edges` (
    `player_low_uuid` BINARY(16) NOT NULL,
    `player_high_uuid` BINARY(16) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`player_low_uuid`, `player_high_uuid`),
    KEY `idx_edges_high` (`player_high_uuid`)
);
```

### `friends_requests`

Solicitudes pendientes.

Las solicitudes expiran. El default recomendado es `7d`, configurable. Al consultar, aceptar o listar pendientes se ignoran las expiradas, y una tarea de limpieza periódica borra registros vencidos para evitar acumulación.

```sql
CREATE TABLE `${tablePrefix}requests` (
    `sender_uuid` BINARY(16) NOT NULL,
    `target_uuid` BINARY(16) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`sender_uuid`, `target_uuid`),
    KEY `idx_requests_target` (`target_uuid`, `created_at`),
    KEY `idx_requests_expires` (`expires_at`)
);
```

### `friends_blocks`

Bloqueos sociales.

```sql
CREATE TABLE `${tablePrefix}blocks` (
    `blocker_uuid` BINARY(16) NOT NULL,
    `blocked_uuid` BINARY(16) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`blocker_uuid`, `blocked_uuid`),
    KEY `idx_blocks_blocked` (`blocked_uuid`)
);
```

### `friends_settings`

Preferencias propias del sistema de amigos.

```sql
CREATE TABLE `${tablePrefix}settings` (
    `player_uuid` BINARY(16) NOT NULL,
    `setting_key` VARCHAR(64) NOT NULL,
    `setting_value` VARCHAR(255) NOT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`player_uuid`, `setting_key`)
);
```

Claves iniciales:

| Key | Default | Uso |
| --- | --- | --- |
| `show_online_status` | `true` | Permite que amigos vean online/offline. |
| `show_activity` | `true` | Permite mostrar actividad rica. |
| `allow_friend_messages` | `true` | Permite mensajes privados de amigos. |
| `allow_friend_broadcasts` | `true` | Permite broadcasts de amigos. |
| `show_connection_notifications` | `true` | Permite avisos de join/quit de amigos. |
| `muted_all_friends` | `false` | Silencia mensajes sociales entrantes. |

### `friends_offline_messages`

Mensajes offline con límite y expiración.

```sql
CREATE TABLE `${tablePrefix}offline_messages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sender_uuid` BINARY(16) NOT NULL,
    `target_uuid` BINARY(16) NOT NULL,
    `message` VARCHAR(512) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `delivered_at` TIMESTAMP NULL,
    `expires_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_offline_target_pending` (`target_uuid`, `delivered_at`, `created_at`),
    KEY `idx_offline_expires` (`expires_at`)
);
```

## Redis, presencia y actividad

Redis es obligatorio en producción porque los plugins Paper publicarán la actividad rica ahí. No se usa para aceptar solicitudes, crear amistades o entregar mensajes offline como fuente de verdad.

Aunque hoy hay un solo proxy, Redis sigue teniendo sentido porque resuelve la comunicación Paper -> Velocity sin acoplar todos los plugins a la base de datos ni obligar a Velocity a preguntar a cada modalidad.

### Keys propuestas

| Key lógica | TTL | Contenido |
| --- | --- | --- |
| `friends:presence:<uuid>` | 90s | JSON con servidor, username, onlineSince, lastHeartbeat. |
| `friends:activity:<uuid>` | 30s | JSON con actividad rica enviada por backend Paper. |
| `friends:last-message:<uuid>` | 10m | UUID del último amigo para `/friend reply`. |
| `friends:language:<uuid>` | sesión | Código `es` o `en` resuelto al entrar al proxy. |

### Canales propuestos

| Canal lógico | Uso |
| --- | --- |
| `friends:activity-updated` | Avisar cambios de actividad rica si se quiere invalidar caché local inmediatamente. |
| `friends:profile-updated` | Invalidar nombre/cache de perfil dentro del proxy. |
| `friends:settings-changed` | Invalidar settings sociales dentro del proxy. |

Pub/Sub es no durable, por eso cada evento solo acelera invalidaciones. Si se pierde, el TTL o la próxima consulta corrige el estado.

### `/friend list` y Redis

`/friend list` no debe consultar Redis key por key. El flujo correcto es:

1. Obtener la lista de UUIDs amigos desde Caffeine; si no está, cargar desde MySQL y cachear.
2. Ordenar/paginar localmente.
3. Para la página visible, construir keys de presencia y actividad.
4. Usar `RedisCache#getMany(Collection<String>)`, que CraftKit implementa con una sola operación `MGET`, deduplica keys y omite faltantes.
5. Guardar el resultado parseado en una caché Caffeine muy corta, por ejemplo `3s` a `5s`, para que spamear `/friend list` no golpee Redis constantemente.
6. Renderizar actividad rica si existe; si falta o expiró, mostrar servidor actual/presencia básica; si tampoco hay presencia, mostrar desconectado.

Esto evita el patrón ineficiente `N amigos = N llamadas Redis`. En una página de 10 amigos se hacen como máximo dos `MGET`: uno para presencia y otro para actividad, o uno solo si se combinan keys en la misma colección.

## Actividad rica

Velocity puede conocer si el jugador está online y en qué backend está conectado. No puede conocer con precisión modo, mapa, partida, equipo o duración interna sin cooperación del servidor Paper.

### V1 con productores Paper

Cada servidor Paper que tenga modos de juego debe publicar actividad directamente en Redis si ya tiene CraftKit. Plugin messaging queda como alternativa si alguna modalidad no puede usar Redis, pero no es el camino principal.

Payload recomendado:

```json
{
  "playerId": "uuid",
  "serverId": "bedwars-1",
  "activityKey": "bedwars.playing",
  "map": "Treenan",
  "mode": "Team BedWars",
  "partySize": 4,
  "startedAt": 1760000000000
}
```

FriendsSystem renderiza el texto según idioma del viewer:

```text
Thefway905 - Jugando Team BedWars en Treenan con 4 jugadores más
```

Si la actividad expira, cae automáticamente a servidor actual o `Online`.

## I18n e integración con NetworkPlayerSettings

La API pública de NetworkPlayerSettings es Paper/Bukkit, por lo que Velocity no debe depender de `ServicesManager` ni eventos Bukkit.

### Estrategia aprobable

1. FriendsSystem lee directamente `${npsTablePrefix}player_settings` para `setting_key = 'language'`.
2. Si el valor es `es`, usa español.
3. Si el valor es `en`, usa inglés.
4. Si el valor es `auto`, Velocity usa `Player#getEffectiveLocale()` si está disponible en la API target; si no, cae al idioma default configurable.
5. Se cachea en memoria durante la sesión del jugador.
6. Si el jugador cambia idioma en Paper, debe hacer rejoin para que FriendsSystem lo lea de nuevo.

La tabla esperada coincide con NetworkPlayerSettings:

```sql
CREATE TABLE `${tablePrefix}player_settings` (
    `player_uuid` BINARY(16) NOT NULL,
    `setting_key` VARCHAR(64) NOT NULL,
    `setting_value` VARCHAR(255) NOT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`player_uuid`, `setting_key`)
);
```

Para tu caso, si la tabla real es `nps_player_settings`, la config debe usar `network-player-settings.table-prefix: "nps_"`. No hace falta otra tabla. FriendsSystem buscará la fila `setting_key = 'language'` y leerá `setting_value`.

### Decisión sobre cambio de idioma en caliente

Para V1 no se hará polling ni refresh por comando. Si un jugador cambia idioma en Paper, FriendsSystem lo verá al próximo rejoin. Es más simple, eficiente y predecible.

## LuckPerms

LuckPerms se obtiene con `LuckPermsProvider.get()` como integración opcional-fuerte: si no está disponible, FriendsSystem puede iniciar con límites default, pero debe loguear warning claro.

### Permisos iniciales

| Permiso | Uso |
| --- | --- |
| `friendssystem.command.friend` | Comando base. |
| `friendssystem.command.message` | Mensajes directos. |
| `friendssystem.command.broadcast` | Broadcast a amigos. |
| `friendssystem.admin.reload` | Reload de config. |
| `friendssystem.admin.debug` | Diagnóstico. |
| `friendssystem.limit.<number>` | Límite de amigos por rango. |
| `friendssystem.bypass.block` | Solo staff autorizado si se aprueba. |

### Metadata

Para prefijos/rangos se usa `CachedMetaData#getPrefix()` y se cachea localmente con TTL corto.

Si el jugador está online, LuckPerms normalmente puede entregar metadata cacheada de forma rápida. Si está offline, técnicamente se puede cargar el `User` con LuckPerms, pero eso puede implicar I/O y no debe hacerse para cada amigo en cada `/friend list`. La UX recomendada es mostrar prefix/rango para amigos online y para offline usar `last_known_prefix`/`last_known_primary_group`, actualizados por FriendsSystem cuando el jugador entra o cuando se consulte metadata de forma segura.

## Cachés locales con Caffeine

| Caché | Key | TTL | Invalidación |
| --- | --- | --- | --- |
| `profiles` | UUID/nombre lower | 10m | profile-updated, login. |
| `friendLists` | UUID | 2m | friendship-changed, remove/add. |
| `pendingRequests` | UUID | 30s | request-created, accept/deny/withdraw. |
| `blocks` | UUID | 2m | block/unblock. |
| `settings` | UUID | 2m | settings-changed. |
| `languages` | UUID | sesión | login/rejoin. |
| `presenceActivityPage` | viewer UUID + page | 3s-5s | TTL corto. |
| `permissionLimits` | UUID | 1m | LuckPerms cached data event si se implementa. |
| `rankMeta` | UUID | 1m | LuckPerms cached data event si se implementa. |

Regla: cachear lecturas repetidas, invalidar en escrituras propias, usar TTL como red de seguridad y nunca asumir Pub/Sub como garantía.

## Flujos críticos

### Enviar solicitud

1. Validar sender jugador, target existente y no igual a sender.
2. Resolver target por perfil local/DB y, si está online, por Velocity.
3. Verificar bloqueos en ambas direcciones.
4. Verificar que no sean amigos y que no exista solicitud cruzada.
5. Verificar límite del sender y límite potencial del target al aceptar.
6. Insertar request con expiración en DB.
7. Invalidar caches de pending.
8. Si target online, enviar mensaje localizado con botones `Aceptar` y `Rechazar`.
9. Reproducir sonido/actionbar si está habilitado y soportado.

### Aceptar solicitud

1. Buscar request `sender -> target`.
2. En transacción: borrar request, insertar edge normalizada, verificar duplicado y límites finales.
3. Invalidar friendLists y pending de ambos.
4. Publicar evento `friendship-changed`.
5. Notificar a ambos si están online.

### Mensaje a amigo

1. Validar amistad, bloqueos, settings y mute.
2. Si target online y permite mensajes, enviar componente localizado a target y confirmación a sender.
3. Guardar `last-message` para reply de ambos con TTL.
4. Si target offline y mensajes offline están habilitados, persistir offline message con límite por target.
5. Si target bloqueó o desactivó mensajes, dar feedback claro sin filtrar información sensible de privacidad.

### Join del jugador

1. Upsert de perfil por UUID/nombre.
2. Cargar settings sociales, idioma y lista de amigos de forma async.
3. Escribir presencia Redis con TTL.
4. Entregar mensajes offline pendientes con límite por lote.
5. Notificar a amigos online que permitan avisos y puedan verlo.
6. Programar heartbeat de presencia mientras esté conectado.

### Quit del jugador

1. Actualizar `last_seen_at`.
2. Eliminar o dejar expirar presencia Redis.
3. Invalidar estado local del jugador.
4. Notificar a amigos online según settings.

## UX y mensajes

Los mensajes se definen en archivos por idioma con MiniMessage:

```text
messages/es.yml
messages/en.yml
```

Patrones de UX:

| Caso | Feedback |
| --- | --- |
| Solicitud recibida | Chat con hover, botones click `Aceptar`/`Rechazar`, actionbar breve y sonido suave. |
| Solicitud enviada | Confirmación con nombre/rango si está disponible. |
| Error recuperable | Mensaje corto con causa exacta y sugerencia. |
| Listado | Paginación, estado, actividad y hover con detalles. |
| Mensaje privado | Formato claro `Tú -> Amigo` y `Amigo -> Tú`, click para responder. |
| Offline messages | Resumen al entrar y entrega limitada para evitar spam. |

Los sonidos deben ser configurables y desactivables porque Velocity documenta limitaciones según versión/emisor.

## Configuración

`config.yml` inicial:

```yaml
config-version: 1

database:
  host: "127.0.0.1"
  port: 3306
  database: "hera_network"
  username: "friends"
  password: "change-me"
  table-prefix: "friends_"

network-player-settings:
  enabled: true
  table-prefix: "nps_"
  default-language: "es"

redis:
  enabled: true
  key-prefix: "hera"
  environment: "prod"
  server-id: "velocity-1"

friends:
  request-expiration: "7d"
  default-limit: 50
  max-offline-messages: 20
  offline-message-expiration: "14d"

permissions:
  limit-prefix: "friendssystem.limit."
  default-limit: 50

cache:
  friends-ttl: "2m"
  settings-ttl: "2m"
  activity-page-ttl: "5s"
  presence-ttl: "90s"

feedback:
  actionbar: true
  sounds: true
```

## Lifecycle y threading

- En constructor Velocity solo guardar dependencias inyectadas.
- En `ProxyInitializeEvent` cargar config, inicializar DB/Redis, correr migraciones y registrar comandos/listeners.
- En `ProxyShutdownEvent` cancelar tasks, cerrar Redis y cerrar Database.
- CraftKit DB y Redis son async; no usar `.join()` durante comandos o eventos de jugadores.
- En startup sí se permite esperar migraciones antes de activar features.
- Velocity scheduler es async; no existe main thread como Paper.
- LuckPerms permite consultas thread-safe, pero cargas remotas deben tratarse como costosas.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
| --- | --- |
| Actividad rica incompleta desde Velocity | Diseñar contrato Paper->Velocity y fallback limpio a servidor actual. |
| Pub/Sub perdido | MySQL/Redis state con TTL como verdad recalculable. |
| Spam de mensajes o solicitudes | Rate limits configurables por comando y expiración de requests. |
| Listas grandes por rangos altos | Paginación, queries indexadas, cache local y no cargar metadata LP offline en masa. |
| Idioma desactualizado | Decisión explícita: requiere rejoin tras cambiar idioma. |
| Privacidad | Settings por jugador, bloqueos y mensajes que no revelen más de lo necesario. |
| Sonidos no soportados | Config desactivable y fallback a chat/actionbar. |

## Plan de implementación recomendado

1. Bootstrap Velocity: clase principal, config BoostedYAML, lifecycle, migraciones CraftKit.
2. Modelo SQL y repositorios puros con tests de normalización UUID/par de amistad.
3. Servicios de amistad: add/accept/deny/withdraw/remove/block/list.
4. Cloud commands con mensajes Adventure localizados.
5. Presence básica Velocity + Caffeine + Redis heartbeat.
6. Mensajería privada, reply, broadcast y offline messages.
7. LuckPerms limits/prefix cache.
8. NetworkPlayerSettings DB language gateway.
9. Actividad rica por Redis desde Paper.
10. Hardening: rate limits, debug command, reload controlado y pruebas de concurrencia.

## Verificación

- `./gradlew test` para lógica pura, parsing de config y normalización de pares UUID.
- `./gradlew build` para Shadow/relocation.
- Pruebas manuales en Velocity con dos jugadores: solicitudes, botones, mensajes, bloqueos, límites y listados.
- Prueba de Redis obligatorio: si Redis no conecta en producción, el plugin debe fallar temprano o desactivar features sociales según config de desarrollo.
- Prueba con LuckPerms ausente/presente.
- Prueba de idioma: `es`, `en`, `auto` y fallback.

## Decisiones pendientes para aprobación

| Decisión | Recomendación |
| --- | --- |
| ¿Redis obligatorio en producción? | Sí. |
| ¿Actividad rica entra en V1? | Sí, mediante plugins Paper publicando actividad en Redis. |
| ¿Idioma instantáneo al cambiar setting? | No; requiere rejoin. |
| ¿Permitir mensajes offline? | Sí, con límite por target y expiración. |
| ¿`/friend clear`? | No se incluye en V1. |
| ¿Prefijos LP para amigos offline? | Solo si están cacheados o guardados como último valor conocido; no cargar LP remoto por cada fila del listado. |
