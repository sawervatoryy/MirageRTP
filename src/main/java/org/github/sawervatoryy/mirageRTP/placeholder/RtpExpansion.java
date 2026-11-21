package org.github.sawervatoryy.mirageRTP.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.github.sawervatoryy.mirageRTP.MirageRTP;

import java.util.Locale;

/** {@code %miragertp_cooldown_<режим>%} — оставшееся время кулдауна или «Готово». */
public final class RtpExpansion extends PlaceholderExpansion {

    private final MirageRTP plugin;

    public RtpExpansion(MirageRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "miragertp";
    }

    @Override
    public String getAuthor() {
        return "sawervatoryy";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("cooldown_")) {
            String modeId = p.substring("cooldown_".length());
            if (!plugin.settings().modes().containsKey(modeId)) {
                return "";
            }
            long left = plugin.cooldowns().remainingSeconds(player.getUniqueId(), modeId);
            return left <= 0 ? plugin.lang().raw("placeholder-ready") : plugin.lang().formatDuration(left);
        }
        return null;
    }
}
