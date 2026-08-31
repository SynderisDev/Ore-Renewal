package dev.synderis.orerenewal.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreProfileSavedDataTest {
    private static final ResourceLocation COPPER = ResourceLocation.fromNamespaceAndPath("example", "ore_copper");
    private static final ResourceLocation TIN = ResourceLocation.fromNamespaceAndPath("example", "ore_tin");
    private static final ResourceLocation LEAD = ResourceLocation.fromNamespaceAndPath("example", "ore_lead");

    @Test
    void firstScanCreatesBaselineWithoutMigration() {
        OreProfileSavedData data = new OreProfileSavedData();

        OreProfileSavedData.ReconcileResult result = data.reconcile(Set.of(COPPER));

        assertTrue(result.baselineCreated());
        assertEquals(0, data.revision());
        assertTrue(data.pendingFeaturesAfter(0).isEmpty());
    }

    @Test
    void laterFeaturesBecomeOneMigration() {
        OreProfileSavedData data = new OreProfileSavedData();
        data.reconcile(Set.of(COPPER));

        OreProfileSavedData.ReconcileResult result = data.reconcile(Set.of(COPPER, TIN, LEAD));

        assertFalse(result.baselineCreated());
        assertEquals(Set.of(TIN, LEAD), result.added());
        assertEquals(1, data.revision());
        assertEquals(Map.of(TIN, false, LEAD, false), data.pendingFeaturesAfter(0));
        assertTrue(data.pendingFeaturesAfter(1).isEmpty());
    }

    @Test
    void chunksSeveralRevisionsBehindReceiveEveryAddition() {
        OreProfileSavedData data = new OreProfileSavedData();
        data.reconcile(Set.of(COPPER));
        data.reconcile(Set.of(COPPER, TIN));
        data.reconcile(Set.of(COPPER, TIN, LEAD));

        assertEquals(Map.of(TIN, false, LEAD, false), data.pendingFeaturesAfter(0));
        assertEquals(Map.of(LEAD, false), data.pendingFeaturesAfter(1));
        assertEquals(2, data.revision());
    }

    @Test
    void automaticAdditionOverridesConservativeHistoricalDuplicate() {
        OreProfileSavedData data = new OreProfileSavedData();
        data.reconcile(Set.of(COPPER));
        data.reconcile(Set.of(COPPER, TIN));
        data.forceHistoricalMigration(Set.of(COPPER, TIN));

        assertEquals(Map.of(COPPER, true, TIN, false), data.pendingFeaturesAfter(0));
    }

    @Test
    void profileRoundTripPreservesRevisionsAndHistoricalPolicy() {
        OreProfileSavedData original = new OreProfileSavedData();
        original.reconcile(Set.of(COPPER));
        original.reconcile(Set.of(COPPER, TIN));
        original.forceHistoricalMigration(Set.of(LEAD));

        CompoundTag encoded = original.save(new CompoundTag(), null);
        OreProfileSavedData restored = OreProfileSavedData.load(encoded, null);

        assertEquals(2, restored.revision());
        assertEquals(3, restored.knownFeatureCount());
        assertEquals(2, restored.migrationCount());
        assertEquals(Map.of(TIN, false, LEAD, true), restored.pendingFeaturesAfter(0));
        assertEquals(Map.of(LEAD, true), restored.pendingFeaturesAfter(1));
        assertTrue(restored.pendingFeaturesAfter(2).isEmpty());
    }

    @Test
    void removingAndReaddingAFeatureCreatesANewExactMigration() {
        OreProfileSavedData data = new OreProfileSavedData();
        data.reconcile(Set.of(COPPER, TIN));

        OreProfileSavedData.ReconcileResult removal = data.reconcile(Set.of(COPPER));
        OreProfileSavedData.ReconcileResult readdition = data.reconcile(Set.of(COPPER, TIN));

        assertEquals(Set.of(TIN), removal.removed());
        assertEquals(Set.of(TIN), readdition.added());
        assertEquals(1, data.revision());
        assertEquals(Map.of(TIN, false), data.pendingFeaturesAfter(0));
    }
}
