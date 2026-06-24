# ProxySettings

ProxySettings es el companion Velocity de `NetworkPlayerSettings`. Su objetivo es exponer, desde el proxy, una API Java simple para que otros plugins Velocity puedan consumir ajustes globales de jugador sin duplicar consultas SQL, cache, catálogos YAML ni lógica de renderizado.

## Qué problema resuelve

`NetworkPlayerSettings` vive del lado Paper/Bukkit y expone servicios por Bukkit Services, eventos Paper y PlaceholderAPI. Eso no existe en Velocity. ProxySettings cubre ese hueco leyendo la misma base de datos y los mismos catálogos de estilos/assets desde Velocity.

Con ProxySettings, un plugin consumidor puede obtener:

- idioma efectivo del jugador;
- settings cacheados por jugador;
- país efectivo;
- visibilidad de bandera del país;
- asset/base64 de bandera;
- `Component` Adventure para bandera como player head;
- `TagResolver` MiniMessage para `<country_flag>`;
- catálogo de nick styles y chat styles;
- nick formateado con el style seleccionado;
- mensaje de chat formateado con el style seleccionado;
- carga individual o por lote para listas.

## Principios del diseño actual

- ProxySettings solo consume datos existentes. No crea ni migra la tabla de `NetworkPlayerSettings`.
- `NetworkPlayerSettings` sigue siendo el dueño de persistencia, validación de permisos, selección de styles y escritura de settings.
- Velocity no valida permisos de styles. Si un style está guardado en DB, ProxySettings intenta aplicarlo.
- La cache es por sesión. Se carga al entrar al proxy y se invalida al salir si la config lo permite.
- Los cambios hechos en Paper se reflejan en Velocity al reconectar al proxy.
- No hay Redis ni invalidación cross-platform en esta versión.
- Los catálogos YAML se cargan y validan al iniciar el plugin.

## Documentos

- [Instalación y build](instalacion-build.md): cómo compilar, qué JAR usar y cómo se sombrea.
- [Configuración](configuracion.md): keys reales de `config.yml`, defaults y conexión DB.
- [Arquitectura y runtime](arquitectura-runtime.md): lifecycle, cache, carga de catálogos y comportamiento operativo.
- [API pública](api-publica.md): referencia completa de `ProxySettingsApi`, modelos y contratos.
- [Settings e idioma](settings-idioma.md): snapshots, claves persistidas y resolución de idioma.
- [Países y banderas](paises-banderas.md): assets, player head components y `<country_flag>`.
- [Nick y chat styles](styles.md): catálogos, renderizado y reglas actuales.
- [Guía para plugins consumidores](integracion-consumidores.md): ejemplos prácticos para integrar correctamente.
- [Limitaciones y decisiones](limitaciones-decisiones.md): lo que hace, lo que no hace y por qué.
- [Referencia de archivos](referencia-archivos.md): mapa rápido del código fuente y recursos.

## Código fuente relevante

| Área | Ruta |
|---|---|
| Bootstrap Velocity | `src/main/java/com/stephanofer/proxysettings/ProxySettingsPlugin.java` |
| API pública | `src/main/java/com/stephanofer/proxysettings/api/ProxySettingsApi.java` |
| Provider estático | `src/main/java/com/stephanofer/proxysettings/api/ProxySettingsProvider.java` |
| Implementación API/cache | `src/main/java/com/stephanofer/proxysettings/settings/DefaultProxySettingsApi.java` |
| SQL settings | `src/main/java/com/stephanofer/proxysettings/settings/storage/SqlPlayerSettingsRepository.java` |
| Config YAML | `src/main/java/com/stephanofer/proxysettings/config/ProxySettingsYamlLoader.java` |
| País/bandera | `src/main/java/com/stephanofer/proxysettings/country` |
| Styles | `src/main/java/com/stephanofer/proxysettings/style` |
| Lifecycle Velocity | `src/main/java/com/stephanofer/proxysettings/platform/velocity/VelocityConnectionListener.java` |
