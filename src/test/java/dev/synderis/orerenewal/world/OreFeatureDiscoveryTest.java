package dev.synderis.orerenewal.world;

import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreFeatureDiscoveryTest {
    @Test
    void standardOreGeneratorsStillRequireAnOreLikeOutput() {
        assertTrue(OreFeatureDiscovery.isSupportedGenerator(Feature.ORE));
        assertTrue(OreFeatureDiscovery.isSupportedGenerator(Feature.SCATTERED_ORE));
        assertFalse(OreFeatureDiscovery.passesSafetyBoundary(true, false, false),
                "An ORE-shaped geology feature must not be treated as an ore");
        assertTrue(OreFeatureDiscovery.passesSafetyBoundary(true, false, true));
    }

    @Test
    void oreOutputIdentityAcceptsTagAndConventionalNamesButRejectsGeology() {
        assertTrue(OreFeatureDiscovery.hasOreIdentity(true, "ancient_debris"));
        assertTrue(OreFeatureDiscovery.hasOreIdentity(false, "tin_ore"));
        assertTrue(OreFeatureDiscovery.hasOreIdentity(false, "ore_tin"));
        assertTrue(OreFeatureDiscovery.hasOreIdentity(false, "deepslate_ore_tin_cluster"));
        assertFalse(OreFeatureDiscovery.hasOreIdentity(false, "limestone"));
        assertFalse(OreFeatureDiscovery.hasOreIdentity(false, "ore"));
    }

    @Test
    void placedFeatureAllowlistOnlyBypassesOutputPolicyForSupportedGenerators() {
        assertTrue(OreFeatureDiscovery.passesSafetyBoundary(true, true, false),
                "A standard generator can be explicitly declared safe despite a non-ore output name");
        assertFalse(OreFeatureDiscovery.passesSafetyBoundary(false, true, true),
                "The placed-feature allowlist must not authorize an arbitrary generator implementation");
    }

    @Test
    void onlyTheExactSupportedCustomGeneratorClassIsAccepted() {
        assertTrue(OreFeatureDiscovery.isExplicitlySupportedCustomGeneratorClass(
                "mekanism.common.world.ResizableOreFeature"));
        assertFalse(OreFeatureDiscovery.isExplicitlySupportedCustomGeneratorClass(
                "example.world.ResizableOreFeature"));
        assertFalse(OreFeatureDiscovery.isExplicitlySupportedCustomGeneratorClass(
                "mekanism.common.world.ResizableOreFeatureChild"));
        assertFalse(OreFeatureDiscovery.isSupportedGenerator(Feature.NO_OP));
    }

    @Test
    void safelyRecognizedOresAreScannedAtEveryGenerationStep() {
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            assertTrue(OreFeatureDiscovery.shouldDiscoverAtStep(true, step.ordinal(), false),
                    () -> "Safe ore was excluded from " + step);
        }
    }

    @Test
    void nonstandardOptInIsLimitedToTheUndergroundOreStep() {
        int undergroundOres = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();

        assertFalse(OreFeatureDiscovery.shouldDiscoverAtStep(false, undergroundOres, false));
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            assertEquals(step == GenerationStep.Decoration.UNDERGROUND_ORES,
                    OreFeatureDiscovery.shouldDiscoverAtStep(false, step.ordinal(), true),
                    () -> "Unexpected nonstandard discovery policy for " + step);
        }
    }
}
