package dev.synderis.orerenewal.world;

import dev.synderis.orerenewal.OreRenewal;
import dev.synderis.orerenewal.config.OreRenewalConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.Tags;

import java.lang.reflect.InvocationTargetException;
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

    public static boolean hasProducedOreBlock(LevelChunk chunk, PlacedFeature placedFeature) {
        Set<Block> producedBlocks = new LinkedHashSet<>();
        placedFeature.getFeatures().forEach(configured ->
                addProducedBlocks(configured.config(), producedBlocks));

        if (producedBlocks.isEmpty()) {
            return false;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.maybeHas(state -> producedBlocks.contains(state.getBlock()))) {
                return true;
            }
        }
        return false;
    }

    private static void addMekanismProducedBlocks(Object config, Set<Block> output) {
        try {
            Object targets = config.getClass().getMethod("targetStates").invoke(config);
            if (targets instanceof Iterable<?> iterable) {
                for (Object target : iterable) {
                    if (target instanceof OreConfiguration.TargetBlockState targetState) {
                        output.add(targetState.state.getBlock());
                    }
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            OreRenewal.LOGGER.warn(
                    "Could not inspect Mekanism ore outputs for conservative historical migration", exception);
        }
    }

    private static void addProducedBlocks(Object config, Set<Block> output) {
        if (config instanceof OreConfiguration ore) {
            addProducedBlocks(ore.targetStates, output);
        } else if ("mekanism.common.world.ResizableOreFeatureConfig".equals(config.getClass().getName())) {
            addMekanismProducedBlocks(config, output);
        }
    }

    private static void addProducedBlocks(
            Iterable<OreConfiguration.TargetBlockState> targets,
            Set<Block> output
    ) {
        for (OreConfiguration.TargetBlockState target : targets) {
            output.add(target.state.getBlock());
        }
    }

    private static void addOreFeatures(Holder<Biome> biome, Map<ResourceLocation, Integer> output) {
        List<net.minecraft.core.HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
        for (int step = 0; step < steps.size(); step++) {
            final int featureStep = step;
            steps.get(step).stream()
                    .filter(holder -> holder.unwrapKey()
                            .map(key -> shouldDiscoverAtStep(
                                    isSafeOre(holder.value(), key.location()),
                                    featureStep,
                                    OreRenewalConfig.INCLUDE_NONSTANDARD_UNDERGROUND_FEATURES.get()))
                            .orElse(false))
                    .map(Holder::unwrapKey)
                    .flatMap(java.util.Optional::stream)
                    .map(ResourceKey::location)
                    .forEach(id -> output.merge(id, featureStep, Math::min));
        }
    }

    private static boolean isSafeOre(PlacedFeature placedFeature, ResourceLocation featureId) {
        return placedFeature.getFeatures().anyMatch(configured -> {
            boolean supportedGenerator = isSupportedGenerator(configured.feature());
            boolean allowlisted = OreRenewalConfig.ADDITIONAL_SAFE_ORE_FEATURES.get().stream()
                    .map(String::valueOf)
                    .anyMatch(featureId.toString()::equals);

            Set<Block> producedBlocks = new LinkedHashSet<>();
            if (supportedGenerator && !allowlisted) {
                addProducedBlocks(configured.config(), producedBlocks);
            }
            boolean producesOreBlock = producedBlocks.stream().anyMatch(OreFeatureDiscovery::isOreBlock);
            return passesSafetyBoundary(supportedGenerator, allowlisted, producesOreBlock);
        });
    }

    static boolean shouldDiscoverAtStep(
            boolean safelyRecognizedOre,
            int featureStep,
            boolean includeNonstandardUndergroundFeatures
    ) {
        return safelyRecognizedOre
                || (featureStep == ORE_STEP && includeNonstandardUndergroundFeatures);
    }

    static boolean isSupportedGenerator(Feature<?> generator) {
        return generator == Feature.ORE
                || generator == Feature.SCATTERED_ORE
                || isExplicitlySupportedCustomGeneratorClass(generator.getClass().getName());
    }

    static boolean isExplicitlySupportedCustomGeneratorClass(String generatorClassName) {
        return SAFE_CUSTOM_ORE_FEATURE_CLASSES.contains(generatorClassName);
    }

    static boolean passesSafetyBoundary(
            boolean supportedGenerator,
            boolean explicitlyAllowlisted,
            boolean producesOreBlock
    ) {
        return supportedGenerator && (explicitlyAllowlisted || producesOreBlock);
    }

    private static boolean isOreBlock(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return hasOreIdentity(block.defaultBlockState().is(Tags.Blocks.ORES), path);
    }

    static boolean hasOreIdentity(boolean taggedAsOre, String registryPath) {
        return taggedAsOre
                || registryPath.endsWith("_ore")
                || registryPath.startsWith("ore_")
                || registryPath.contains("_ore_");
    }
}
