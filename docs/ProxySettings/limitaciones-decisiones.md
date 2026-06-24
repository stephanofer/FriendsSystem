# Limitaciones y Decisiones

Este documento lista decisiones explícitas del diseño actual de ProxySettings y límites que un consumidor debe conocer.

## ProxySettings no escribe settings

ProxySettings solo lee.

No existen métodos públicos para:

- cambiar idioma;
- cambiar país;
- cambiar visibilidad de bandera;
- cambiar nick style;
- cambiar chat style.

La escritura sigue perteneciendo a `NetworkPlayerSettings` del lado Paper.

## ProxySettings no crea tablas

No hay migraciones activas.

La tabla `${tablePrefix}player_settings` debe existir antes de iniciar ProxySettings. Esa tabla pertenece a `NetworkPlayerSettings`.

## No hay Redis en esta versión

No se implementó Redis ni pub/sub.

Consecuencia:

- si un jugador cambia una setting en Paper;
- y sigue conectado al proxy;
- Velocity no se entera inmediatamente.

La actualización se refleja al reconectar al proxy o si un consumidor llama explícitamente a `invalidate(...)` y luego `load(...)`.

## Cache sin TTL

No hay `expireAfterWrite` ni `expireAfterAccess`.

La cache se controla por:

- `refresh(...)` al entrar;
- `invalidate(...)` al salir si `cache-cleanup-on-disconnect` está activo;
- `invalidateAll()` en shutdown.

Esto fue decidido para evitar sincronización parcial no determinística.

## No hay hot reload de catálogos

Los archivos:

- `assets/countries.yml`;
- `styles/nick-patterns.yml`;
- `styles/chat-patterns.yml`;

se cargan al iniciar el plugin.

Si se modifican, hay que reiniciar el proxy para que ProxySettings los vuelva a cargar.

## No hay checks de permisos en Velocity

ProxySettings no depende de LuckPerms ni de ningún sistema de permisos.

Si un jugador tiene un `nick_style` o `chat_style` guardado y existe en el catálogo, ProxySettings lo aplica.

La validación de permisos ocurre en `NetworkPlayerSettings` cuando el jugador selecciona styles del lado Paper.

## No hay PlaceholderAPI

Velocity no tiene PlaceholderAPI en este proyecto.

ProxySettings ofrece alternativas Adventure:

- métodos que devuelven `Component`;
- `TagResolver` para `<country_flag>`;
- render de nick/chat style por API Java.

## No hay listener automático de chat

ProxySettings no modifica ningún chat global del proxy.

Cada consumidor decide dónde aplicar:
```java
formatChatMessage(UUID, Component)
```

Esto evita acoplar ProxySettings a un sistema específico de chat.

## `countryAsset(UUID)` no respeta visibilidad

Esto es intencional.

`countryAsset(UUID)` devuelve metadata del país efectivo. Para respetar `show_country_flag`, usar:

- `countryHeadTexture(UUID)`;
- `countryFlag(UUID)`;
- `countryFlagResolver(UUID)`.

## Defaults cuando no hay cache

`getCachedOrDefault(UUID)` devuelve defaults si no hay snapshot cargado.

Defaults actuales:

| Setting | Default |
|---|---|
| `language` | `auto` |
| `detected_country` | `XX` |
| `country_override` | `""` |
| `show_country_flag` | `true` |
| `nick_style` | `""` |
| `chat_style` | `""` |

Esto permite que renderizados tolerantes no fallen, pero un consumidor que necesita datos persistidos debe llamar `load(...)` o `loadMany(...)`.

## Catálogos inválidos fallan startup

ProxySettings prefiere fallar temprano antes que arrancar con datos corruptos.

Fallan startup, entre otros casos:

- `countries` ausente o vacío;
- falta `XX` en countries;
- base64 inválido;
- style sin `<name>` o `<message>`;
- ID de style inválido;
- catálogos de style vacíos;
- IDs duplicados.

## Futuro recomendado

La mejora natural futura es invalidación cross-platform:

1. `NetworkPlayerSettings` persiste una setting en Paper.
2. Publica evento por Redis.
3. ProxySettings recibe evento.
4. ProxySettings invalida y recarga el snapshot afectado.

Esto permitiría actualización inmediata sin relog, pero no pertenece al MVP actual.
