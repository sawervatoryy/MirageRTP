package org.github.sawervatoryy.mirageRTP.manager;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Settings;
import org.github.sawervatoryy.mirageRTP.model.Pit;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;
import org.github.sawervatoryy.mirageRTP.model.TeleportOptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Хранилище РТП-ям и логика срабатывания при входе игрока в зону. */
public final class PitManager {

    private final MirageRTP plugin;
    private final Map<String, Pit> pits = new ConcurrentHashMap<>();
    /** Игроки, находящиеся в яме прямо сейчас (имя ямы) — чтобы срабатывать только на вход. */
    private final Map<UUID, String> inside = new ConcurrentHashMap<>();
    /** Время последнего срабатывания — внутренний анти-спам. */
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public PitManager(MirageRTP plugin) {
        this.plugin = plugin;
    }

    public void replaceAll(Map<String, Pit> loaded) {
        pits.clear();
        pits.putAll(loaded);
    }

    public Collection<Pit> all() {
        return pits.values();
    }

    public Optional<Pit> get(String name) {
        return Optional.ofNullable(pits.get(name));
    }

    public boolean create(String name, String mode) {
        return pits.putIfAbsent(name, new Pit(name, mode, null, null, null)) == null;
    }

    public boolean delete(String name) {
        return pits.remove(name) != null;
    }

    public void update(Pit pit) {
        pits.put(pit.name(), pit);
    }

    /** Сериализует снимок в главном потоке, а запись на диск выполняет асинхронно. */
    public void saveAsync() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("pits");
        pits.values().forEach(p -> p.write(root.createSection(p.name())));
        String data = yaml.saveToString();
        File file = new File(plugin.getDataFolder(), "pits.yml");
        plugin.asyncExecutor().execute(() -> {
            synchronized (fileLock) {
                try {
                    Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить pits.yml", e);
                }
            }
        });
    }

    public Pit find(Location location) {
        for (Pit pit : pits.values()) {
            if (pit.contains(location)) {
                return pit;
            }
        }
        return null;
    }

    /** Стоит ли игрок сейчас внутри какой-либо ямы. */
    public boolean isInside(UUID id) {
        return inside.containsKey(id);
    }

    public void forget(UUID id) {
        inside.remove(id);
        lastTrigger.remove(id);
    }

    /** Вызывается при смене блока игроком. */
    public void handleMove(Player player, Location to) {
        UUID id = player.getUniqueId();
        Pit pit = find(to);
        if (pit == null) {
            inside.remove(id);
            return;
        }
        String previous = inside.put(id, pit.name());
        if (pit.name().equals(previous)) {
            return;
        }
        trigger(player, pit);
    }

    private void trigger(Player player, Pit pit) {
        Settings settings = plugin.settings();
        UUID id = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(id);
        if (last != null && now - last < settings.pit().antiSpamMillis()) {
            return;
        }
        lastTrigger.put(id, now);

        // что бы ни случилось дальше (кулдаун, нет денег, поиск не удался) — падение в яме не должно убивать
        plugin.teleports().grantFallGrace(id);

        RtpMode mode = settings.modes().get(pit.mode());
        if (mode == null) {
            plugin.lang().send(player, "pit-mode-missing", "pit", pit.name(), "mode", pit.mode());
            return;
        }
        TeleportManager tp = plugin.teleports();
        if (tp.hasSession(id) && !tp.isCountingDown(id)) {
            return; // уже идёт поиск точки
        }
        // яма важнее обычного отсчёта — тихо отменяем его
        tp.cancelCountdown(player, null);

        settings.effect("pit-enter").play(player.getLocation());
        plugin.lang().title(player, "pit-enter-title", "pit-enter-subtitle", "mode", mode.displayName(), "pit", pit.name());
        plugin.lang().actionBar(player, "pit-enter-actionbar", "mode", mode.displayName());
        // замораживаем на месте, чтобы игрок не продолжал падать, пока ищется точка
        tp.freeze(player);
        tp.request(player, mode, TeleportOptions.pit(settings.pit()));
        if (!tp.hasSession(id)) {
            tp.unfreeze(player); // запрос отклонён (кулдаун, нет денег...) — отпускаем, падение безопасно за счёт защиты в яме
        }
    }
}
