package org.github.sawervatoryy.mirageRTP.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Lang;
import org.github.sawervatoryy.mirageRTP.config.Settings;
import org.github.sawervatoryy.mirageRTP.model.BlockPos;
import org.github.sawervatoryy.mirageRTP.model.Pit;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;
import org.github.sawervatoryy.mirageRTP.model.TeleportOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** {@code /rtp} и подкоманды администратора. */
public final class RtpCommand implements CommandExecutor, TabCompleter {

    private static final Pattern PIT_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String ADMIN = "miragertp.admin";

    private final MirageRTP plugin;

    public RtpCommand(MirageRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        Settings settings = plugin.settings();

        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return true;
            }
            if (!player.hasPermission("miragertp.use")) {
                lang.send(player, "no-permission");
                return true;
            }
            if (settings.useGui()) {
                plugin.menu().open(player);
            } else {
                RtpMode def = settings.modes().get(settings.defaultMode());
                if (def == null) {
                    lang.send(player, "unknown-mode", "mode", settings.defaultMode());
                } else {
                    plugin.teleports().request(player, def, TeleportOptions.normal());
                }
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> reload(sender);
            case "force" -> force(sender, args);
            case "debug" -> {
                if (requireAdmin(sender)) {
                    boolean on = plugin.toggleDebug();
                    sender.sendMessage(net.kyori.adventure.text.Component.text("[MirageRTP] debug: " + (on ? "ON" : "OFF")
                            + " (build immunity-6)"));
                }
            }
            case "pit" -> pit(sender, args);
            default -> {
                RtpMode mode = settings.modes().get(sub);
                if (mode == null) {
                    lang.send(sender, "unknown-mode", "mode", args[0]);
                    return true;
                }
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (!player.hasPermission("miragertp.use")) {
                    lang.send(player, "no-permission");
                    return true;
                }
                plugin.teleports().request(player, mode, TeleportOptions.normal());
            }
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        plugin.lang().send(sender, "players-only");
        return null;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        plugin.lang().send(sender, "no-permission");
        return false;
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        long start = System.currentTimeMillis();
        plugin.reloadAsync().whenComplete((v, err) -> {
            if (err != null) {
                plugin.getLogger().warning("Ошибка перезагрузки: " + err.getMessage());
                plugin.lang().send(sender, "reload-failed");
            } else {
                plugin.lang().send(sender, "reload-done", "ms", System.currentTimeMillis() - start);
            }
        });
    }

    private void force(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Lang lang = plugin.lang();
        if (args.length < 2) {
            lang.send(sender, "usage-force");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            lang.send(sender, "player-not-found", "player", args[1]);
            return;
        }
        String modeId = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : plugin.settings().defaultMode();
        RtpMode mode = plugin.settings().modes().get(modeId);
        if (mode == null) {
            lang.send(sender, "unknown-mode", "mode", modeId);
            return;
        }
        plugin.teleports().request(target, mode, TeleportOptions.force());
        lang.send(sender, "force-done", "player", target.getName(), "mode", mode.displayName());
    }

    private void pit(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Lang lang = plugin.lang();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "create" -> {
                if (args.length < 4) {
                    lang.send(sender, "usage-pit");
                    return;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                String modeId = args[3].toLowerCase(Locale.ROOT);
                if (!PIT_NAME.matcher(name).matches()) {
                    lang.send(sender, "pit-invalid-name");
                    return;
                }
                if (!plugin.settings().modes().containsKey(modeId)) {
                    lang.send(sender, "unknown-mode", "mode", modeId);
                    return;
                }
                if (!plugin.pits().create(name, modeId)) {
                    lang.send(sender, "pit-exists", "pit", name);
                    return;
                }
                plugin.pits().saveAsync();
                lang.send(sender, "pit-created", "pit", name, "mode", modeId);
            }
            case "delete" -> {
                if (args.length < 3) {
                    lang.send(sender, "usage-pit");
                    return;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                if (plugin.pits().delete(name)) {
                    plugin.pits().saveAsync();
                    lang.send(sender, "pit-deleted", "pit", name);
                } else {
                    lang.send(sender, "pit-not-found", "pit", name);
                }
            }
            case "setpos1", "setpos2" -> {
                if (args.length < 3) {
                    lang.send(sender, "usage-pit");
                    return;
                }
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                Optional<Pit> opt = plugin.pits().get(name);
                if (opt.isEmpty()) {
                    lang.send(sender, "pit-not-found", "pit", name);
                    return;
                }
                Pit old = opt.get();
                String world = player.getWorld().getName();
                BlockPos pos = BlockPos.of(player.getLocation());
                boolean first = sub.equals("setpos1");
                Pit updated = first ? old.withPos1(world, pos) : old.withPos2(world, pos);
                plugin.pits().update(updated);
                plugin.pits().saveAsync();
                lang.send(player, "pit-pos-set", "pit", name, "pos", first ? "1" : "2", "x", pos.x(), "y", pos.y(), "z", pos.z());
                boolean otherDropped = first ? (old.pos2() != null && updated.pos2() == null)
                        : (old.pos1() != null && updated.pos1() == null);
                if (otherDropped) {
                    lang.send(player, "pit-world-reset");
                }
            }
            case "list" -> {
                var all = plugin.pits().all();
                if (all.isEmpty()) {
                    lang.send(sender, "pit-list-empty");
                    return;
                }
                lang.send(sender, "pit-list-header", "count", all.size());
                for (Pit p : all) {
                    lang.send(sender, p.isComplete() ? "pit-list-entry" : "pit-list-entry-incomplete",
                            "pit", p.name(), "mode", p.mode(), "world", p.world() == null ? "-" : p.world());
                }
            }
            default -> lang.send(sender, "usage-pit");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        Settings settings = plugin.settings();
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            settings.modes().values().stream()
                    .filter(m -> !(sender instanceof Player p) || m.canUse(p))
                    .forEach(m -> out.add(m.id()));
            if (admin) {
                out.addAll(List.of("reload", "force", "pit", "debug"));
            }
        } else if (admin && args[0].equalsIgnoreCase("force")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 3) {
                out.addAll(settings.modes().keySet());
            }
        } else if (admin && args[0].equalsIgnoreCase("pit")) {
            if (args.length == 2) {
                out.addAll(List.of("create", "delete", "setpos1", "setpos2", "list"));
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
                plugin.pits().all().forEach(p -> out.add(p.name()));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
                out.addAll(settings.modes().keySet());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
