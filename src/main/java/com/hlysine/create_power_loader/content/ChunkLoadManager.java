package com.hlysine.create_power_loader.content;

import com.hlysine.create_power_loader.CPLBlockEntityTypes;
import com.hlysine.create_power_loader.CreatePowerLoader;
import com.mojang.logging.LogUtils;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;

public class ChunkLoadManager {
    private static final TicketController TICKET_CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(CreatePowerLoader.MODID, "chunk_loader_chunks"),
            ChunkLoadManager::validateTickets
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAVED_CHUNKS_DISCARD_TICKS = 100;

    private static final List<Pair<UUID, Set<LoadedChunkPos>>> unforceQueue = new LinkedList<>();
    private static final Map<UUID, Set<LoadedChunkPos>> savedForcedChunks = new HashMap<>();
    private static int savedChunksDiscardCountdown = SAVED_CHUNKS_DISCARD_TICKS;
    /**
     * Only accessible during GlobalRailwayManager#tick
     */
    public static Level tickLevel;

    public static final Map<LoaderMode, WeakCollection<ChunkLoader>> allLoaders = new HashMap<>();

    public static void addLoader(LoaderMode mode, ChunkLoader loader) {
        allLoaders.computeIfAbsent(mode, $ -> new WeakCollection<>()).add(loader);
    }

    public static void removeLoader(LoaderMode mode, ChunkLoader loader) {
        allLoaders.computeIfAbsent(mode, $ -> new WeakCollection<>()).remove(loader);
    }

    public static void onServerWorldTick(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide())
            return;

        MinecraftServer server = event.getLevel().getServer();
        if (savedChunksDiscardCountdown == 0) {
            for (Map.Entry<UUID, Set<LoadedChunkPos>> entry : savedForcedChunks.entrySet()) {
                unforceAllChunks(server, entry.getKey(), entry.getValue());
            }
            savedForcedChunks.clear();
        } else if (savedChunksDiscardCountdown > 0)
            savedChunksDiscardCountdown--;

        if (!unforceQueue.isEmpty()) {
            for (Pair<UUID, Set<LoadedChunkPos>> pair : unforceQueue) {
                unforceAllChunks(server, pair.getFirst(), pair.getSecond());
            }
            unforceQueue.clear();
        }
    }

    public static <T extends Comparable<? super T>> void updateForcedChunks(MinecraftServer server, LoadedChunkPos center, T owner, int loadingRange, Set<LoadedChunkPos> forcedChunks) {
        Set<LoadedChunkPos> targetChunks = getChunksAroundCenter(center, loadingRange);
        updateForcedChunks(server, targetChunks, owner, forcedChunks);
    }

    public static <T extends Comparable<? super T>> void updateForcedChunks(MinecraftServer server, Collection<LoadedChunkPos> centers, T owner, int loadingRange, Set<LoadedChunkPos> forcedChunks) {
        Set<LoadedChunkPos> targetChunks = new HashSet<>();
        for (LoadedChunkPos center : centers) {
            targetChunks.addAll(getChunksAroundCenter(center, loadingRange));
        }
        updateForcedChunks(server, targetChunks, owner, forcedChunks);
    }

    public static <T extends Comparable<? super T>> void updateForcedChunks(MinecraftServer server, Collection<LoadedChunkPos> newChunks, T owner, Set<LoadedChunkPos> forcedChunks) {
        Set<LoadedChunkPos> unforcedChunks = new HashSet<>();
        for (LoadedChunkPos chunk : forcedChunks) {
            if (newChunks.contains(chunk)) {
                newChunks.remove(chunk);
            } else {
                forceChunk(server, owner, chunk.dimension(), chunk.x(), chunk.z(), false);
                unforcedChunks.add(chunk);
            }
        }
        forcedChunks.removeAll(unforcedChunks);
        for (LoadedChunkPos chunk : newChunks) {
            forceChunk(server, owner, chunk.dimension(), chunk.x(), chunk.z(), true);
            forcedChunks.add(chunk);
        }
        if (unforcedChunks.size() > 0 || newChunks.size() > 0)
            LOGGER.debug("CPL: update chunks, unloaded {}, loaded {}.", unforcedChunks.size(), newChunks.size());
    }

    public static void enqueueUnforceAll(UUID owner, Set<LoadedChunkPos> forcedChunks) {
        unforceQueue.add(Pair.of(owner, forcedChunks));
    }

    public static <T extends Comparable<? super T>> void unforceAllChunks(MinecraftServer server, T owner, Set<LoadedChunkPos> forcedChunks) {
        for (LoadedChunkPos chunk : forcedChunks) {
            forceChunk(server, owner, chunk.dimension(), chunk.x(), chunk.z(), false);
        }
        if (forcedChunks.size() > 0)
            LOGGER.debug("CPL: unload all, unloaded {} chunks.", forcedChunks.size());
        forcedChunks.clear();
    }

    private static Set<LoadedChunkPos> getChunksAroundCenter(LoadedChunkPos center, int radius) {
        Set<LoadedChunkPos> ret = new HashSet<>();
        for (int i = center.x() - radius + 1; i <= center.x() + radius - 1; i++) {
            for (int j = center.z() - radius + 1; j <= center.z() + radius - 1; j++) {
                ret.add(new LoadedChunkPos(center.dimension(), i, j));
            }
        }
        return ret;
    }

    private static <T extends Comparable<? super T>> void forceChunk(MinecraftServer server, T owner, ResourceLocation dimension, int chunkX, int chunkZ, boolean add) {
        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        assert targetLevel != null;
        if (owner instanceof BlockPos) {
            TICKET_CONTROLLER.forceChunk(targetLevel, (BlockPos) owner, chunkX, chunkZ, add, true);
        } else {
            TICKET_CONTROLLER.forceChunk(targetLevel, (UUID) owner, chunkX, chunkZ, add, true);
        }
    }

    public static Set<LoadedChunkPos> getSavedForcedChunks(UUID entityUUID) {
        return savedForcedChunks.remove(entityUUID);
    }

    public static void validateTickets(ServerLevel level, TicketHelper helper) {
        helper.getBlockTickets().forEach((blockPos, tickets) -> {
            LOGGER.debug("CPL: Inspecting level {} position {} which has {} non-ticking tickets and {} ticking tickets.",
                    level.dimension().location(),
                    blockPos.toShortString(),
                    tickets.nonTicking().size(),
                    tickets.ticking().size());
            AbstractChunkLoaderBlockEntity blockEntity = level.getBlockEntity(blockPos, CPLBlockEntityTypes.BRASS_CHUNK_LOADER.get()).orElse(null);
            if (blockEntity == null)
                blockEntity = level.getBlockEntity(blockPos, CPLBlockEntityTypes.ANDESITE_CHUNK_LOADER.get()).orElse(null);
            if (blockEntity == null) {
                helper.removeAllTickets(blockPos);
                LOGGER.debug("CPL: level {} position {} unforced: Cannot find block entity.", level.dimension().location(), blockPos.toShortString());
                return;
            }

            for (Long chunk : tickets.nonTicking()) {
                ChunkPos chunkPos = new ChunkPos(chunk);
                helper.removeTicket(blockPos, chunk, false);
                LOGGER.debug("CPL: level {} position {} unforced non-ticking {}", level.dimension().location(), blockPos.toShortString(), chunkPos);
            }

            Set<LoadedChunkPos> forcedChunks = new HashSet<>();
            for (Long chunk : tickets.ticking()) {
                ChunkPos chunkPos = new ChunkPos(chunk);
                forcedChunks.add(new LoadedChunkPos(level.dimension().location(), chunkPos));
            }
            blockEntity.reclaimChunks(forcedChunks);
            LOGGER.debug("CPL: level {} position {} reclaimed {} chunks.", level.dimension().location(), blockPos.toShortString(), forcedChunks.size());
        });

        helper.getEntityTickets().forEach((entityUUID, tickets) -> {
            // We can't get entities at this point
            // Saving the chunk list for the movement behaviours to use

            Set<LoadedChunkPos> savedChunks = new HashSet<>();
            if (savedForcedChunks.containsKey(entityUUID)) {
                savedChunks = savedForcedChunks.get(entityUUID);
            }
            for (Long chunk : tickets.nonTicking()) {
                savedChunks.add(new LoadedChunkPos(level, chunk));
            }
            for (Long chunk : tickets.ticking()) {
                savedChunks.add(new LoadedChunkPos(level, chunk));
            }
            savedForcedChunks.put(entityUUID, savedChunks);
            LOGGER.debug("CPL: Inspecting entity {} which has {} non-ticking tickets and {} ticking tickets.",
                    entityUUID,
                    tickets.nonTicking().size(),
                    tickets.ticking().size());
        });
        savedChunksDiscardCountdown = SAVED_CHUNKS_DISCARD_TICKS;
    }

    public static void reclaimChunks(Level level, UUID owner, Map<ResourceKey<Level>, Set<LoadedChunkPos>> reclaimedChunks) {
        // Unload saved chunks when new ones are ready
        // Perhaps this is overcomplicating things because levels with forced chunks should always be loaded?
        Set<LoadedChunkPos> oldChunks = getSavedForcedChunks(owner);
        if (oldChunks != null) {
            for (LoadedChunkPos chunk : oldChunks) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, chunk.dimension());
                Set<LoadedChunkPos> reclaim = reclaimedChunks.get(key);
                if (reclaim != null) {
                    reclaim.add(chunk);
                } else {
                    reclaim = new HashSet<>();
                    reclaim.add(chunk);
                    reclaimedChunks.put(key, reclaim);
                }
            }
        }

        if (!reclaimedChunks.isEmpty()) {
            MinecraftServer server = level.getServer();
            assert server != null;
            for (Iterator<Map.Entry<ResourceKey<Level>, Set<LoadedChunkPos>>> iterator = reclaimedChunks.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<ResourceKey<Level>, Set<LoadedChunkPos>> entry = iterator.next();
                ServerLevel reclaimLevel = server.getLevel(entry.getKey());
                if (reclaimLevel != null) {
                    unforceAllChunks(server, owner, entry.getValue());
                    iterator.remove();
                }
            }
        }
    }

    public static void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(TICKET_CONTROLLER);
    }

    public record LoadedChunkPos(@NotNull ResourceLocation dimension, @NotNull ChunkPos chunkPos) {

        public LoadedChunkPos(@NotNull Level level, long chunkPos) {
            this(level.dimension().location(), new ChunkPos(chunkPos));
        }

        public LoadedChunkPos(@NotNull ResourceLocation level, int pX, int pZ) {
            this(level, new ChunkPos(pX, pZ));
        }

        public LoadedChunkPos(@NotNull Level level, BlockPos blockPos) {
            this(level.dimension().location(), new ChunkPos(blockPos));
        }

        public int x() {
            return this.chunkPos.x;
        }

        public int z() {
            return this.chunkPos.z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LoadedChunkPos loadedChunk)) return false;
            if (!Objects.equals(loadedChunk.dimension, this.dimension)) return false;
            if (!Objects.equals(loadedChunk.chunkPos, this.chunkPos)) return false;
            return true;
        }

        @Override
        public String toString() {
            return dimension + ":" + chunkPos;
        }
    }
}
