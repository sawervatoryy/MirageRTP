package org.github.sawervatoryy.mirageRTP;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.github.sawervatoryy.mirageRTP.command.RtpCommand;
import org.github.sawervatoryy.mirageRTP.config.ConfigSnapshot;
import org.github.sawervatoryy.mirageRTP.config.Lang;
import org.github.sawervatoryy.mirageRTP.config.MenuSettings;
import org.github.sawervatoryy.mirageRTP.config.Settings;
import org.github.sawervatoryy.mirageRTP.gui.RtpMenu;
import org.github.sawervatoryy.mirageRTP.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageRTP.listener.MenuListener;
import org.github.sawervatoryy.mirageRTP.listener.MoveListener;
import org.github.sawervatoryy.mirageRTP.listener.PlayerListener;
import org.github.sawervatoryy.mirageRTP.manager.CooldownManager;
import org.github.sawervatoryy.mirageRTP.manager.PitManager;
import org.github.sawervatoryy.mirageRTP.manager.TeleportManager;
import org.github.sawervatoryy.mirageRTP.placeholder.RtpExpansion;
import org.github.sawervatoryy.mirageRTP.service.EconomyService;
import org.github.sawervatoryy.mirageRTP.service.LocationFinder;
import org.github.sawervatoryy.mirageRTP.task.PitAmbientTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MirageRTP extends JavaPlugin {

    private final Executor asyncExecutor = r -> Bukkit.getScheduler().runTaskAsynchronously(this, r);
    private final Executor mainExecutor = r -> Bukkit.getScheduler().runTask(this, r);

    // неизменяемые снимки конфигов; подменяются целиком при (пере)загрузке
    private volatile Settings settings;
    private volatile Lang lang;
    private volatile MenuSettings menus;

    private CooldownManager cooldowns;
    private EconomyService economy;
    private LocationFinder finder;
    private TeleportManager teleports;
    private PitManager pits;
    private RtpMenu menu;
    private WorldGuardHook worldGuard;
    private BukkitTask ambientTask;
    /** Включается командой /rtp debug без правки конфига. */
    private volatile boolean debugOverride;

    @Override
    public void onEnable() {
        getLogger().info("MirageRTP " + getPluginMeta().getVersion() + " (build immunity-6) загружается");
        cooldowns = new CooldownManager();
        economy = new EconomyService(this);
        finder = new LocationFinder(this);
        teleports = new TeleportManager(this);
        pits = new PitManager(this);
        menu = new RtpMenu(this);

        // первичная загрузка — синхронно (сервер ещё стартует, игроков нет)
        try {
            apply(ConfigSnapshot.read(this));
        } catch (Exception e) {
            getLogger().severe("Не удалось загрузить конфиги: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                worldGuard = new WorldGuardHook();
                getLogger().info("Интеграция с WorldGuard включена.");
            } catch (Throwable t) {
                getLogger().warning("WorldGuard найден, но API недоступно: " + t.getMessage());
            }
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RtpExpansion(this).register();
        }

        getServer().getPluginManager().registerEvents(new MoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        RtpCommand command = new RtpCommand(this);
        var cmd = getCommand("rtp");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        if (ambientTask != null) {
            ambientTask.cancel();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
    }

    /** Читает конфиги в асинхронном потоке и применяет результат в главном. */
    public CompletableFuture<Void> reloadAsync() {
        return CompletableFuture.supplyAsync(() -> ConfigSnapshot.read(this), asyncExecutor)
                .thenAcceptAsync(this::apply, mainExecutor);
    }

    private void apply(ConfigSnapshot snapshot) {
        this.settings = snapshot.settings();
        this.lang = snapshot.lang();
        this.menus = snapshot.menus();
        this.pits.replaceAll(snapshot.pits());

        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
        Settings.Ambient ambient = snapshot.settings().pit().ambient();
        if (ambient.enabled()) {
            ambientTask = new PitAmbientTask(this).runTaskTimer(this, 20L, ambient.intervalTicks());
        }
    }

    public boolean isDebug() { return debugOverride || settings.debug(); }
    public boolean toggleDebug() { debugOverride = !debugOverride; return debugOverride; }
    public Settings settings() { return settings; }
    public Lang lang() { return lang; }
    public MenuSettings menus() { return menus; }
    public CooldownManager cooldowns() { return cooldowns; }
    public EconomyService economy() { return economy; }
    public LocationFinder finder() { return finder; }
    public TeleportManager teleports() { return teleports; }
    public PitManager pits() { return pits; }
    public RtpMenu menu() { return menu; }
    /** @return хук WorldGuard либо {@code null}, если плагин не установлен */
    public WorldGuardHook worldGuard() { return worldGuard; }
    public Executor asyncExecutor() { return asyncExecutor; }
}
