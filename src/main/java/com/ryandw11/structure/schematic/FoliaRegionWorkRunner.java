package com.ryandw11.structure.schematic;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Runs chunk-owned work on the correct Folia region thread with a per-tick time budget.
 */
public final class FoliaRegionWorkRunner<T extends FoliaRegionWorkRunner.RegionWorkItem> {
    private final Plugin plugin;
    private final World world;
    private final int maxWorkPerTick;
    private final long maxNanosPerTick;
    private final Queue<ChunkWorkQueue<T>> queues;
    private final Consumer<T> consumer;
    private final Runnable completion;

    public FoliaRegionWorkRunner(Plugin plugin, World world, Collection<T> work, int maxWorkPerTick,
                                 double maxMillisPerTick, Consumer<T> consumer, Runnable completion) {
        this.plugin = plugin;
        this.world = world;
        this.maxWorkPerTick = Math.max(1, maxWorkPerTick);
        this.maxNanosPerTick = Math.max(250_000L, (long) (maxMillisPerTick * 1_000_000L));
        this.consumer = consumer;
        this.completion = completion;
        this.queues = groupByChunk(work);
    }

    public void start() {
        scheduleNext(1L);
    }

    private Queue<ChunkWorkQueue<T>> groupByChunk(Collection<T> work) {
        Map<Long, ChunkWorkQueue<T>> grouped = new HashMap<>();
        for (T item : work) {
            long key = chunkKey(item.getChunkX(), item.getChunkZ());
            grouped.computeIfAbsent(key, ignored -> new ChunkWorkQueue<>(item.getChunkX(), item.getChunkZ()))
                    .items.add(item);
        }
        return new ArrayDeque<>(grouped.values());
    }

    private void scheduleNext(long delayTicks) {
        ChunkWorkQueue<T> queue = queues.poll();
        if (queue == null) {
            completion.run();
            return;
        }

        RegionScheduler scheduler = plugin.getServer().getRegionScheduler();
        scheduler.runDelayed(plugin, world, queue.chunkX, queue.chunkZ, task -> {
            long started = System.nanoTime();
            int processed = 0;

            while (!queue.items.isEmpty() && processed < maxWorkPerTick) {
                T item = queue.items.poll();
                try {
                    consumer.accept(item);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "A scheduled schematic paste operation failed.", ex);
                }
                processed++;

                if (processed > 0 && System.nanoTime() - started >= maxNanosPerTick) {
                    break;
                }
            }

            if (!queue.items.isEmpty()) {
                queues.add(queue);
            }
            scheduleNext(1L);
        }, delayTicks);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    public interface RegionWorkItem {
        int getChunkX();

        int getChunkZ();
    }

    private static final class ChunkWorkQueue<T> {
        private final int chunkX;
        private final int chunkZ;
        private final Queue<T> items = new ArrayDeque<>();

        private ChunkWorkQueue(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
