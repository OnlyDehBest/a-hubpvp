package it.onlynelchilling.ahubpvp.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class CounterTask {
    private final int reach;
    private final BooleanSupplier shouldStop;
    private final Runnable onStop;
    private final IntConsumer onTick;
    private final Runnable onComplete;
    private BukkitTask task;
    private int remaining;

    private CounterTask(int reach, BooleanSupplier shouldStop, Runnable onStop, IntConsumer onTick, Runnable onComplete) {
        this.reach = reach;
        this.shouldStop = shouldStop;
        this.onStop = onStop;
        this.onTick = onTick;
        this.onComplete = onComplete;
    }

    public void start(JavaPlugin plugin) {
        remaining = reach;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (shouldStop.getAsBoolean()) {
                task.cancel();
                onStop.run();
                return;
            }
            if (remaining <= 0) {
                task.cancel();
                onComplete.run();
                return;
            }
            onTick.accept(remaining);
            remaining--;
        }, 20L, 20L);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int reach = 0;
        private BooleanSupplier shouldStop = () -> false;
        private Runnable onStop = () -> {};
        private IntConsumer onTick = i -> {};
        private Runnable onComplete = () -> {};

        public Builder reach(int reach) { this.reach = reach; return this; }
        public Builder shouldStop(BooleanSupplier shouldStop) { this.shouldStop = shouldStop; return this; }
        public Builder onStop(Runnable onStop) { this.onStop = onStop; return this; }
        public Builder onTick(IntConsumer onTick) { this.onTick = onTick; return this; }
        public Builder onComplete(Runnable onComplete) { this.onComplete = onComplete; return this; }

        public CounterTask build() {
            return new CounterTask(reach, shouldStop, onStop, onTick, onComplete);
        }
    }
}
