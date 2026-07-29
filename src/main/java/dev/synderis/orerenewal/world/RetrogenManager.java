package dev.synderis.orerenewal.world;

import dev.synderis.orerenewal.OreRenewal;
import dev.synderis.orerenewal.config.OreRenewalConfig;
import dev.synderis.orerenewal.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class RetrogenManager {
    private static final int NEW_CHUNK_SENTINEL = -1;
    private final ConcurrentLinkedQueue<ChunkKey> queue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkKey> queued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkKey> loaded = ConcurrentHashMap.newKeySet();
    private final Map<ResourceKey<Level>, Map<ResourceLocation, Integer>> currentFeatures = new ConcurrentHashMap<>();
    private final AtomicLong processedChunks = new AtomicLong();
    private final AtomicLong featureRuns = new AtomicLong();
    private final AtomicLong successfulPlacements = new AtomicLong();
    private final AtomicLong failedFeatureRuns = new AtomicLong();
    private volatile boolean profilesReady;

    public void onServerStarted(ServerStartedEvent event) {
        profilesReady = false;
        currentFeatures.clear();
        processedChunks.set(0);
        featureRuns.set(0);
        successfulPlacements.set(0);
        failedFeatureRuns.set(0);

        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<ResourceLocation, Integer> discovered = OreFeatureDiscovery.discover(level);
            currentFeatures.put(level.dimension(), discovered);

            OreProfileSavedData profile = OreProfileSavedData.get(level);
            OreProfileSavedData.ReconcileResult result = profile.reconcile(discovered.keySet());
            if (result.baselineCreated()) {
                OreRenewal.LOGGER.info(
                        "Created Ore Renewal baseline for {} with {} underground ore features",
                        level.dimension().location(), discovered.size());
            } else {
                if (!result.added().isEmpty()) {
                    OreRenewal.LOGGER.info(
                            "Ore Renewal revision {} for {} adds: {}",
                            result.revision(), level.dimension().location(), formatIds(result.added()));
                }
                if (!result.removed().isEmpty()) {
                    OreRenewal.LOGGER.info(
                            "Ore Renewal noticed removed features in {}: {}",
                            level.dimension().location(), formatIds(result.removed()));
                }
            }
        }

        profilesReady = true;
        enqueueAllLoaded();
    }

    public void onServerStopped(ServerStoppedEvent event) {
        profilesReady = false;
        queue.clear();
        queued.clear();
        loaded.clear();
        currentFeatures.clear();
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        ChunkKey key = new ChunkKey(level.dimension(), chunk.getPos().toLong());
        loaded.add(key);

        if (event.isNewChunk()) {
            chunk.setData(ModAttachments.CHUNK_REVISION, NEW_CHUNK_SENTINEL);
            chunk.setUnsaved(true);
        }
        if (profilesReady) {
            enqueueLoadedNeighborhood(key);
        } else {
            enqueue(key);
        }
    }

    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            loaded.remove(new ChunkKey(level.dimension(), event.getChunk().getPos().toLong()));
        }
    }

    public void onServerTick(ServerTickEvent.Post event) {
        if (!profilesReady || !OreRenewalConfig.ENABLED.get()) {
            return;
        }
        if (OreRenewalConfig.ONLY_WHEN_TICK_HAS_TIME.get() && !event.hasTime()) {
            return;
        }

        int budget = OreRenewalConfig.CHUNKS_PER_TICK.get();
        for (int i = 0; i < budget; i++) {
            ChunkKey key = queue.poll();
            if (key == null) {
                break;
            }
            queued.remove(key);
            process(event.getServer(), key);
        }
    }

    private void process(MinecraftServer server, ChunkKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null || !loaded.contains(key)) {
            return;
        }

        ChunkPos pos = new ChunkPos(key.position());
        if (!hasLoadedNeighborhood(key)) {
            return;
        }
        ChunkAccess access = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (!(access instanceof LevelChunk chunk)) {
            return;
        }

        OreProfileSavedData profile = OreProfileSavedData.get(level);
        int completedRevision = chunk.hasData(ModAttachments.CHUNK_REVISION)
                ? chunk.getData(ModAttachments.CHUNK_REVISION)
                : 0;

        if (completedRevision == NEW_CHUNK_SENTINEL) {
            markComplete(chunk, profile.revision());
            return;
        }
        if (completedRevision >= profile.revision()) {
            return;
        }

        Map<ResourceLocation, Integer> available = currentFeatures.getOrDefault(level.dimension(), Map.of());
        Set<ResourceLocation> pending = profile.pendingFeaturesAfter(completedRevision);
        pending.retainAll(available.keySet());

        if (!pending.isEmpty()) {
            primeWorldgenHeightmaps(level, chunk.getPos());
        }
        for (ResourceLocation featureId : pending) {
            OptionalInt step = OreFeatureDiscovery.findStepInChunk(chunk, featureId);
            if (step.isPresent()) {
                runFeature(level, chunk, featureId, step.getAsInt());
            }
        }

        markComplete(chunk, profile.revision());
        long processed = processedChunks.incrementAndGet();
        int logInterval = OreRenewalConfig.LOG_EVERY_N_CHUNKS.get();
        if (logInterval > 0 && processed % logInterval == 0) {
            OreRenewal.LOGGER.info(
                    "Ore Renewal processed {} chunks this session ({} feature runs, {} successful placements, {} failures, {} queued)",
                    processed, featureRuns.get(), successfulPlacements.get(), failedFeatureRuns.get(), queued.size());
        }
    }

    private void runFeature(ServerLevel level, LevelChunk chunk, ResourceLocation featureId, int generationStep) {
        Registry<PlacedFeature> registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, featureId);
        Holder<PlacedFeature> holder = registry.getHolder(key).orElse(null);
        if (holder == null) {
            OreRenewal.LOGGER.warn("Queued placed feature {} no longer exists; skipping it", featureId);
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        BlockPos origin = new BlockPos(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ());
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
        random.setFeatureSeed(decorationSeed, featureId.toString().hashCode() & Integer.MAX_VALUE, generationStep);

        try {
            boolean placed = holder.value().placeWithBiomeCheck(
                    level, level.getChunkSource().getGenerator(), random, origin);
            featureRuns.incrementAndGet();
            if (placed) {
                successfulPlacements.incrementAndGet();
            }
        } catch (RuntimeException exception) {
            failedFeatureRuns.incrementAndGet();
            OreRenewal.LOGGER.error(
                    "Failed to apply placed feature {} to chunk [{}, {}] in {}",
                    featureId, chunkPos.x, chunkPos.z, level.dimension().location(), exception);
        }
    }

    private static void primeWorldgenHeightmaps(ServerLevel level, ChunkPos center) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                ChunkAccess access = level.getChunkSource().getChunk(
                        center.x + offsetX, center.z + offsetZ, ChunkStatus.FULL, false);
                if (access instanceof LevelChunk chunk) {
                    primeWorldgenHeightmaps(chunk);
                }
            }
        }
    }

    private static void primeWorldgenHeightmaps(LevelChunk chunk) {
        EnumSet<Heightmap.Types> missing = EnumSet.noneOf(Heightmap.Types.class);
        if (!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG)) {
            missing.add(Heightmap.Types.OCEAN_FLOOR_WG);
        }
        if (!chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG)) {
            missing.add(Heightmap.Types.WORLD_SURFACE_WG);
        }
        if (!missing.isEmpty()) {
            Heightmap.primeHeightmaps(chunk, missing);
        }
    }

    private static void markComplete(LevelChunk chunk, int revision) {
        chunk.setData(ModAttachments.CHUNK_REVISION, revision);
        chunk.setUnsaved(true);
    }

    private void enqueue(ChunkKey key) {
        if (queued.add(key)) {
            queue.add(key);
        }
    }

    private void enqueueAllLoaded() {
        loaded.forEach(this::enqueue);
    }

    private boolean hasLoadedNeighborhood(ChunkKey center) {
        ChunkPos pos = new ChunkPos(center.position());
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                ChunkKey neighbor = new ChunkKey(
                        center.dimension(),
                        ChunkPos.asLong(pos.x + offsetX, pos.z + offsetZ));
                if (!loaded.contains(neighbor)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void enqueueLoadedNeighborhood(ChunkKey center) {
        ChunkPos pos = new ChunkPos(center.position());
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                ChunkKey neighbor = new ChunkKey(
                        center.dimension(),
                        ChunkPos.asLong(pos.x + offsetX, pos.z + offsetZ));
                if (loaded.contains(neighbor)) {
                    enqueue(neighbor);
                }
            }
        }
    }

    public int forceFeature(MinecraftServer server, ResourceLocation featureId) {
        int dimensions = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Map<ResourceLocation, Integer> available = currentFeatures.getOrDefault(level.dimension(), Map.of());
            if (available.containsKey(featureId)) {
                OreProfileSavedData.get(level).forceMigration(Set.of(featureId));
                dimensions++;
            }
        }
        if (dimensions > 0) {
            enqueueAllLoaded();
        }
        return dimensions;
    }

    public Set<ResourceLocation> currentFeatures() {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        currentFeatures.values().forEach(features -> result.addAll(features.keySet()));
        return result;
    }

    public Collection<LevelStatus> status(MinecraftServer server) {
        Collection<LevelStatus> result = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            OreProfileSavedData profile = OreProfileSavedData.get(level);
            result.add(new LevelStatus(
                    level.dimension().location(),
                    profile.revision(),
                    profile.knownFeatureCount(),
                    profile.migrationCount()));
        }
        return result;
    }

    public long queuedChunkCount() {
        return queued.size();
    }

    public long processedChunkCount() {
        return processedChunks.get();
    }

    public long featureRunCount() {
        return featureRuns.get();
    }

    public long successfulPlacementCount() {
        return successfulPlacements.get();
    }

    private static String formatIds(Collection<ResourceLocation> ids) {
        return ids.stream().map(ResourceLocation::toString).sorted().toList().toString();
    }

    private record ChunkKey(ResourceKey<Level> dimension, long position) {
    }

    public record LevelStatus(ResourceLocation dimension, int revision, int knownFeatures, int migrations) {
    }
}
