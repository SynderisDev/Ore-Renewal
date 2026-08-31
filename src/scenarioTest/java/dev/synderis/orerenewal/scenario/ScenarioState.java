package dev.synderis.orerenewal.scenario;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class ScenarioState extends SavedData {
    private static final String DATA_NAME = "ore_renewal_lifecycle_test";
    private static final SavedData.Factory<ScenarioState> FACTORY =
            new SavedData.Factory<>(ScenarioState::new, ScenarioState::load);

    private final CompoundTag values = new CompoundTag();

    static ScenarioState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ScenarioState load(CompoundTag tag, HolderLookup.Provider registries) {
        ScenarioState state = new ScenarioState();
        state.values.merge(tag.getCompound("Values"));
        return state;
    }

    int requireInt(String key) {
        if (!values.contains(key)) {
            throw new IllegalStateException("Lifecycle state is missing " + key);
        }
        return values.getInt(key);
    }

    void putInt(String key, int value) {
        values.putInt(key, value);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Values", values.copy());
        return tag;
    }
}
