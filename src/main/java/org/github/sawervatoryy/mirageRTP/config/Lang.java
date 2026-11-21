package org.github.sawervatoryy.mirageRTP.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageRTP.model.CostType;
import org.github.sawervatoryy.mirageRTP.util.Text;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Неизменяемый снимок {@code lang.yml}. */
public final class Lang {

    private static final Title.Times DEFAULT_TIMES =
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300));

    private final Map<String, String> messages;

    private Lang(Map<String, String> messages) {
        this.messages = messages;
    }

    public static Lang load(FileConfiguration cfg) {
        Map<String, String> map = new HashMap<>();
        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) {
                map.put(key, cfg.getString(key, ""));
            }
        }
        return new Lang(Map.copyOf(map));
    }

    /** Сырая строка без подстановок. */
    public String raw(String key) {
        return messages.getOrDefault(key, "");
    }

    /** Строка с подстановкой {@code {prefix}} и пар плейсхолдеров. */
    public String format(String key, Object... kv) {
        String raw = messages.get(key);
        if (raw == null) {
            return "";
        }
        return Text.apply(raw.replace("{prefix}", messages.getOrDefault("prefix", "")), kv);
    }

    public Component component(String key, Object... kv) {
        String s = format(key, kv);
        return s.isEmpty() ? Component.empty() : Text.parse(s);
    }

    public void send(CommandSender to, String key, Object... kv) {
        String s = format(key, kv);
        if (!s.isBlank()) {
            to.sendMessage(Text.parse(s));
        }
    }

    public void actionBar(Player player, String key, Object... kv) {
        String s = format(key, kv);
        if (!s.isBlank()) {
            player.sendActionBar(Text.parse(s));
        }
    }

    public void title(Player player, String titleKey, String subtitleKey, Object... kv) {
        title(player, DEFAULT_TIMES, titleKey, subtitleKey, kv);
    }

    public void title(Player player, Title.Times times, String titleKey, String subtitleKey, Object... kv) {
        if (format(titleKey).isBlank() && format(subtitleKey).isBlank()) {
            return;
        }
        player.showTitle(Title.title(component(titleKey, kv), component(subtitleKey, kv), times));
    }

    /** Текст стоимости: «100 монет», «Бесплатно». */
    public String costText(CostType type, double amount) {
        if (type == CostType.NONE || amount <= 0) {
            return raw("cost-none");
        }
        String amountText = amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
        return format("cost-" + type.name().toLowerCase(), "amount", amountText);
    }

    /** Длительность вида «1ч 5м 3с». */
    public String formatDuration(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(raw("time-hours").replace("{n}", String.valueOf(h))).append(' ');
        }
        if (m > 0) {
            sb.append(raw("time-minutes").replace("{n}", String.valueOf(m))).append(' ');
        }
        if (sec > 0 || sb.isEmpty()) {
            sb.append(raw("time-seconds").replace("{n}", String.valueOf(sec)));
        }
        return sb.toString().trim();
    }
}
