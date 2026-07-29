package dev.synderis.orerenewal.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public final class OreProfileSavedData extends SavedData {
    private static final String DATA_NAME = "ore_renewal_profile";
    private static final int SCHEMA_VERSION = 2;
    private static final SavedData.Factory<OreProfileSavedData> FACTORY =
            new SavedData.Factory<>(OreProfileSavedData::new, OreProfileSavedData::load);

    private boolean initialized;
    private int revision;
    private final Set<ResourceLocation> knownFeatures = new LinkedHashSet<>();
    private final NavigableMap<Integer, Migration> additions = new TreeMap<>();

    public static OreProfileSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static OreProfileSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OreProfileSavedData data = new OreProfileSavedData();
        data.initialized = tag.getBoolean("Initialized");
        data.revision = Math.max(0, tag.getInt("Revision"));
        readLocations(tag.getList("KnownFeatures", Tag.TAG_STRING), data.knownFeatures);

        ListTag migrations = tag.getList("Additions", Tag.TAG_COMPOUND);
        for (int i = 0; i < migrations.size(); i++) {
            CompoundTag migration = migrations.getCompound(i);
            int migrationRevision = migration.getInt("Revision");
            if (migrationRevision > 0) {
                Set<ResourceLocation> features = new LinkedHashSet<>();
                readLocations(migration.getList("Features", Tag.TAG_STRING), features);
                if (!features.isEmpty()) {
                    data.additions.put(
                            migrationRevision,
                            new Migration(features, migration.getBoolean("SkipIfPresent")));
                }
            }
        }
        return data;
    }

    private static void readLocations(ListTag list, Set<ResourceLocation> output) {
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                output.add(id);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putBoolean("Initialized", initialized);
        tag.putInt("Revision", revision);
        tag.put("KnownFeatures", writeLocations(knownFeatures));

        ListTag migrations = new ListTag();
        additions.forEach((migrationRevision, migrationData) -> {
            CompoundTag migration = new CompoundTag();
            migration.putInt("Revision", migrationRevision);
            migration.put("Features", writeLocations(migrationData.features()));
            migration.putBoolean("SkipIfPresent", migrationData.skipIfPresent());
            migrations.add(migration);
        });
        tag.put("Additions", migrations);
        return tag;
    }

    private static ListTag writeLocations(Set<ResourceLocation> locations) {
        ListTag list = new ListTag();
        locations.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .map(StringTag::valueOf)
                .forEach(list::add);
        return list;
    }

    public ReconcileResult reconcile(Set<ResourceLocation> currentFeatures) {
        Set<ResourceLocation> current = new LinkedHashSet<>(currentFeatures);
        if (!initialized) {
            initialized = true;
            knownFeatures.clear();
            knownFeatures.addAll(current);
            setDirty();
            return new ReconcileResult(true, Set.of(), Set.of(), revision);
        }

        Set<ResourceLocation> added = new LinkedHashSet<>(current);
        added.removeAll(knownFeatures);
        Set<ResourceLocation> removed = new LinkedHashSet<>(knownFeatures);
        removed.removeAll(current);

        if (!added.isEmpty()) {
            revision++;
            additions.put(revision, migration(added, false));
        }
        if (!added.isEmpty() || !removed.isEmpty()) {
            knownFeatures.clear();
            knownFeatures.addAll(current);
            setDirty();
        }
        return new ReconcileResult(false, Set.copyOf(added), Set.copyOf(removed), revision);
    }

    public int forceMigration(Set<ResourceLocation> features) {
        return forceMigration(features, false);
    }

    public int forceHistoricalMigration(Set<ResourceLocation> features) {
        return forceMigration(features, true);
    }

    private int forceMigration(Set<ResourceLocation> features, boolean skipIfPresent) {
        if (features.isEmpty()) {
            return revision;
        }
        revision++;
        Migration migration = migration(features, skipIfPresent);
        additions.put(revision, migration);
        knownFeatures.addAll(migration.features());
        initialized = true;
        setDirty();
        return revision;
    }

    private static Migration migration(Set<ResourceLocation> features, boolean skipIfPresent) {
        Set<ResourceLocation> copy = Collections.unmodifiableSet(new LinkedHashSet<>(features));
        return new Migration(copy, skipIfPresent);
    }

    public Map<ResourceLocation, Boolean> pendingFeaturesAfter(int completedRevision) {
        Map<ResourceLocation, Boolean> pending = new LinkedHashMap<>();
        additions.tailMap(completedRevision, false).values().forEach(migration ->
                migration.features().forEach(feature ->
                        pending.merge(feature, migration.skipIfPresent(), (first, second) -> first && second)));
        return pending;
    }

    public int revision() {
        return revision;
    }

    public int knownFeatureCount() {
        return knownFeatures.size();
    }

    public int migrationCount() {
        return additions.size();
    }

    public record ReconcileResult(
            boolean baselineCreated,
            Set<ResourceLocation> added,
            Set<ResourceLocation> removed,
            int revision
    ) {
    }

    private record Migration(Set<ResourceLocation> features, boolean skipIfPresent) {
    }
}
