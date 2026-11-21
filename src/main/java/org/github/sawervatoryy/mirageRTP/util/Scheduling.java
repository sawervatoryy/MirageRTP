package org.github.sawervatoryy.mirageRTP.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Небольшие помощники для возврата выполнения в главный поток. */
public final class Scheduling {

    private Scheduling() {
    }

    /** Выполняет задачу в главном потоке (сразу, если мы уже в нём). */
    public static void main(Plugin plugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
