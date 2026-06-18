# Ejemplos y buenas prácticas para consumidores

Estos ejemplos usan únicamente API pública de NetworkPlayerSettings.

## Obtener servicios de forma segura

```java
import com.stephanofer.networkplayersettings.api.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConsumerPlugin extends JavaPlugin {
    private PlayerSettingsService settings;

    @Override
    public void onEnable() {
        this.settings = Bukkit.getServicesManager().load(PlayerSettingsService.class);
        if (this.settings == null) {
            getLogger().severe("Missing NetworkPlayerSettings PlayerSettingsService");
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}
```

## Esperar datos listos

```java
import com.stephanofer.networkplayersettings.event.PlayerSettingsReadyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SettingsReadyListener implements Listener {
    @EventHandler
    public void onSettingsReady(PlayerSettingsReadyEvent event) {
        String language = event.resolvedLanguage().code();
        String country = event.countryCode();
        // Safe point: NetworkPlayerSettings already marked this player as ready.
    }
}
```

## Leer idioma efectivo en un comando propio

```java
if (!settings.isReady(player.getUniqueId())) {
    player.sendMessage("Your settings are still loading.");
    return;
}

Language language = settings.resolvedLanguage(player);
player.sendMessage("Language: " + language.code());
```

## Leer país y renderizar bandera

```java
UUID playerId = player.getUniqueId();
String countryCode = settings.countryCode(playerId);
String flag = CountryFlag.emoji(countryCode);
player.sendMessage("Country: " + flag + " " + countryCode);
```

## Cambiar idioma sin bloquear el main thread

```java
settings.setLanguage(player.getUniqueId(), LanguagePreference.SPANISH)
    .thenRun(() -> player.getScheduler().run(plugin, task -> {
        player.sendMessage("Language saved.");
    }, null))
    .exceptionally(error -> {
        plugin.getLogger().warning("Could not save language for " + player.getUniqueId() + ": " + error.getMessage());
        player.getScheduler().run(plugin, task -> {
            player.sendMessage("Could not save your language. Try again later.");
        }, null);
        return null;
    });
```

Nota: el `CompletableFuture` de NetworkPlayerSettings completa después de persistir y después de aplicar caché/evento en main thread para las mutaciones públicas actuales.

## Cambiar país manual

```java
settings.setCountryOverride(player.getUniqueId(), "AR")
    .thenRun(() -> plugin.getLogger().info("Country override saved"))
    .exceptionally(error -> {
        plugin.getLogger().warning("Invalid or unsaved country override: " + error.getMessage());
        return null;
    });
```

`setCountryOverride` rechaza códigos inválidos y `XX`. Para volver al país detectado:

```java
settings.clearCountryOverride(player.getUniqueId());
```

## Escuchar cambios

```java
import com.stephanofer.networkplayersettings.api.SettingKey;
import com.stephanofer.networkplayersettings.event.PlayerSettingChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SettingChangeListener implements Listener {
    @EventHandler
    public void onSettingChange(PlayerSettingChangeEvent event) {
        if (event.settingKey() == SettingKey.LANGUAGE) {
            String newResolvedLanguage = event.newResolvedValue();
            // Refresh localized UI, scoreboard, cached messages, etc.
        }
    }
}
```

## Acceder a assets de país

```java
NetworkAssetService assets = Bukkit.getServicesManager().load(NetworkAssetService.class);
if (assets != null) {
    CountryAsset asset = assets.countryAsset(settings.countryCode(player.getUniqueId()));
    String displayName = asset.displayName();
    String texture = asset.headTextureBase64();
}
```

## Do / Don't

| Do | Don't |
|---|---|
| Usá `ServicesManager` para `PlayerSettingsService` y `NetworkAssetService`. | No instancies `DefaultPlayerSettingsService`, repositorios ni loaders internos. |
| Esperá `PlayerSettingsReadyEvent` o `isReady(UUID)`. | No asumas datos listos en cualquier evento temprano de conexión. |
| Encadená `CompletableFuture` para mutaciones. | No hagas `join()`/`get()` en main thread. |
| Usá `setLanguage`, `setCountryOverride`, `clearCountryOverride`. | No escribas directo en MySQL ni uses `setSetting` para claves no escribibles. |
| Tratá `cached(UUID)` como lectura best-effort. | No confundas snapshot default con datos persistidos. |
| Manejás `null` al usar servicios con `softdepend`. | No crashees si NetworkPlayerSettings no está instalado cuando tu integración es opcional. |
| Usá `CountryFlag.normalizeCode` y fallback `XX`. | No confíes en input externo de país sin normalizar. |

## Fallos que debe manejar un consumidor

- Servicio ausente: plugin deshabilitado, startup fallido o integración opcional.
- Future fallido al mutar: DB caída, error de pool o excepción de persistencia.
- Jugador no listo: join todavía no procesado o jugador desconectado.
- Placeholder desactualizado por TTL: el valor puede cachearse hasta `placeholderapi.cache-ttl-millis`.
- País desconocido `XX`: GeoIP apagado, IP no pública, DB faltante o lookup fallido.
