package dev.synderis.orerenewal.world;

import dev.synderis.orerenewal.config.OreRenewalConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class OreFeatureDiscovery {
    private static final int ORE_STEP = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
    private static final Set<String> SAFE_CUSTOM_ORE_FEATURE_CLASSES = Set.of(
            "mekanism.common.world.ResizableOreFeature"
    );

    private OreFeatureDiscovery() {
    }

    public static Map<ResourceLocation, Integer> discover(ServerLevel level) {
        Map<ResourceLocation, Integer> features = new LinkedHashMap<>();
        for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            addOreFeatures(biome, features);
        }
        return Map.copyOf(features);
    }

    public static OptionalInt findStepInChunk(LevelChunk chunk, ResourceLocation featureId) {
        Set<Holder<Biome>> biomes = new LinkedHashSet<>();
        for (LevelChunkSection section : chunk.getSections()) {
            section.getBiomes().getAll(biomes::add);
        }

        for (Holder<Biome> biome : biomes) {
            BiomeGenerationSettings settings = biome.value().getGenerationSettings();
            List<net.minecraft.core.HolderSet<PlacedFeature>> steps = settings.features();
            for (int step = 0; step < steps.size(); step++) {
                boolean present = steps.get(step).stream()
                        .map(Holder::unwrapKey)
                        .flatMap(java.util.Optional::stream)
                        .map(ResourceKey::location)
                        .anyMatch(featureId::equals);
                if (present) {
                    return OptionalInt.of(step);
                }
            }
        }
        return OptionalInt.empty();
    }

    private static void addOreFeatures(Holder<Biome> biome, Map<ResourceLocation, Integer> output) {
        List<net.minecraft.core.HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
        for (int step = 0; step < steps.size(); step++) {
            final int featureStep = step;
            steps.get(step).stream()
                    .filter(holder -> isSafeOre(holder.value())
                            || (featureStep == ORE_STEP
                            && OreRenewalConfig.INCLUDE_NONSTANDARD_UNDERGROUND_FEATURES.get()))
                    .map(Holder::unwrapKey)
                    .flatMap(java.util.Optional::stream)
                    .map(ResourceKey::location)
                    .forEach(id -> output.merge(id, featureStep, Math::min));
        }
    }

    private static boolean isSafeOre(PlacedFeature placedFeature) {
        return placedFeature.getFeatures()
                .anyMatch(configured -> configured.feature() == Feature.ORE
                        || configured.feature() == Feature.SCATTERED_ORE
                        || SAFE_CUSTOM_ORE_FEATURE_CLASSES.contains(
                        configured.feature().getClass().getName()));
    }
}
