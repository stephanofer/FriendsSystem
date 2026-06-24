# Países y Banderas

ProxySettings expone assets de país y banderas renderizadas como componentes Adventure.

## Archivos relevantes

| Área | Archivo |
|---|---|
| Asset público | `src/main/java/com/stephanofer/proxysettings/country/CountryAsset.java` |
| Catálogo | `src/main/java/com/stephanofer/proxysettings/country/CountryAssetCatalog.java` |
| Loader YAML | `src/main/java/com/stephanofer/proxysettings/country/CountryAssetLoader.java` |
| Servicio de bandera | `src/main/java/com/stephanofer/proxysettings/country/CountryFlagService.java` |
| YAML default | `src/main/resources/assets/countries.yml` |

## YAML de países

Formato real:

```yaml
file-version: 1

countries:
  XX:
    name: Unknown
    head-texture-base64: "eyJ0ZXh0dXJlcyI6e319"
    aliases: [unknown]
  AR:
    name: Argentina
    head-texture-base64: "eyJ0ZXh0dXJlcyI6e319"
    aliases: [argentina, south-america]
```

## Validaciones de catálogo

El loader valida:

- existe sección `countries`;
- la sección no está vacía;
- cada entrada es una sección válida;
- el código de país es ISO alpha-2 (`[A-Z]{2}` después de normalizar);
- `head-texture-base64` no está vacío;
- `head-texture-base64` es base64 válido;
- el constructor de `CountryAsset` valida `displayName`, `headTextureBase64` y aliases;
- el catálogo requiere un asset fallback `XX`;
- no permite códigos duplicados;
- no permite aliases duplicados;
- no permite alias que colisionen con un código de país.

Si el catálogo es inválido, el startup falla con `IllegalStateException`.

## `CountryAsset`

Record público:

```java
public record CountryAsset(
    String code,
    String displayName,
    String headTextureBase64,
    Set<String> aliases
) {}
```

Reglas del constructor:

- `code` se normaliza con `CountryFlag.normalizeCode`;
- `displayName` no puede estar vacío;
- `headTextureBase64` no puede estar vacío;
- aliases se normalizan a minúsculas;
- aliases se exponen como `Set.copyOf(...)`.

## País efectivo

`PlayerSettingsSnapshot#countryCode()` usa:

1. `country_override` válido y distinto de `XX`;
2. si no, `detected_country` normalizado;
3. fallback `XX`.

## Visibilidad de bandera

`show_country_flag` controla solo el render player-aware.

Si `show_country_flag = false`:

- `countryHeadTexture(UUID)` devuelve `""`;
- `countryFlag(UUID)` devuelve `Component.empty()`;
- `<country_flag>` inserta `Component.empty()`.

`countryAsset(UUID)` sigue devolviendo el asset del país efectivo. Esto permite consultar metadata aunque la bandera no deba mostrarse.

## `countryHeadTexture(UUID)`

Devuelve el `Value` base64 de la textura del país efectivo si la bandera está activa.

```java
String texture = api.countryHeadTexture(playerId);
if (texture.isBlank()) {
    // El jugador ocultó su bandera.
}
```

## `countryFlag(UUID)`

Devuelve un componente Adventure:

```java
Component flag = api.countryFlag(playerId);
```

Internamente usa:

```java
Component.object(ObjectContents.playerHead()
    .profileProperty(PlayerHeadObjectContents.property("textures", value))
    .build())
```

Si la bandera está oculta, devuelve `Component.empty()`.

## `countryFlagResolver(UUID)`

Devuelve un resolver para MiniMessage:

```text
<country_flag>
```

Ejemplo:

```java
MiniMessage mini = MiniMessage.builder()
    .editTags(tags -> tags.resolver(api.countryFlagResolver(playerId)))
    .build();

Component line = mini.deserialize("<country_flag> <gray>Jugador conectado</gray>");
```

## Ejemplo recomendado en consumidores

```java
Component name = api.formattedNick(playerId, username);
Component flag = api.countryFlag(playerId);

Component line = Component.text()
    .append(flag)
    .append(Component.space())
    .append(name)
    .build();
```

Si el jugador desactivó bandera, `flag` será vacío y el resto seguirá funcionando.
