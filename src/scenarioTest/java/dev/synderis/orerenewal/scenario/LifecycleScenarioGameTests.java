package dev.synderis.orerenewal.scenario;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;

@GameTestHolder(ScenarioTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleScenarioGameTests {
    private static final String PHASE_PROPERTY = "oreRenewal.lifecycle.phase";
    private static final String ORE_RENEWAL = "ore_renewal";
    private static final String FIXTURE_A = "ore_renewal_fixture_a";
    private static final String FIXTURE_B = "ore_renewal_fixture_b";
    private static final ResourceLocation FEATURE_A =
            ResourceLocation.fromNamespaceAndPath(FIXTURE_A, "marker_ore");
    private static final ResourceLocation FEATURE_B =
            ResourceLocation.fromNamespaceAndPath(FIXTURE_B, "marker_ore");
    private static final Block MARKER_A = Blocks.NETHER_QUARTZ_ORE;
    private static final Block MARKER_B = Blocks.NETHER_GOLD_ORE;

    private static final ChunkPos A_EXISTING = new ChunkPos(80, 80);
    private static final ChunkPos A_POST_MOD = new ChunkPos(84, 80);
    private static final ChunkPos B_EXISTING = new ChunkPos(80, 80);
    private static final ChunkPos C_BEFORE_A = new ChunkPos(80, 80);
    private static final ChunkPos C_WITH_A = new ChunkPos(84, 80);
    private static final ChunkPos C_WITH_A_B = new ChunkPos(88, 80);

    private static final int TARGET_A_MIN_Y = 8;
    private static final int TARGET_A_MAX_Y = 24;
    private static final int TARGET_B_MIN_Y = 32;
    private static final int TARGET_B_MAX_Y = 48;
    private static final int PRESENCE_A_Y = 70;
    private static final int PRESENCE_B_Y = 71;
    private static final int SET_BLOCK_FLAGS = 2;
    private static final int SETTLE_TICKS = 80;

    private LifecycleScenarioGameTests() {
    }

    @GameTest(templateNamespace = ORE_RENEWAL, template = "empty3x3x3", timeoutTicks = 600)
    public static void lifecyclePhase(GameTestHelper helper) {
        String phase = System.getProperty(PHASE_PROPERTY, "");
        switch (phase) {
            case "A0" -> scenarioAInitial(helper);
            case "A1" -> scenarioAAddFixture(helper);
            case "A2" -> scenarioANewChunk(helper);
            case "A3" -> scenarioARestart(helper);
            case "B0" -> scenarioBExistingWorld(helper);
            case "B1" -> scenarioBAddOreRenewal(helper);
            case "B2" -> scenarioBAddFixture(helper);
            case "B3" -> scenarioBRestart(helper);
            case "C0" -> scenarioCExistingWorld(helper);
            case "C1" -> scenarioCAddFixtureA(helper);
            case "C2" -> scenarioCAddFixtureB(helper);
            case "C3" -> scenarioCAddOreRenewal(helper);
            case "C4" -> scenarioCRestart(helper);
            default -> helper.fail("Unknown lifecycle phase: " + phase, BlockPos.ZERO);
        }
    }

    private static void scenarioAInitial(GameTestHelper helper) {
        assertMods(helper, true, false, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        prepareCohort(level, A_EXISTING);
        assertMarkerCounts(helper, level, A_EXISTING, 0, 0);
        succeedAfterSettling(helper, () -> assertMarkerCounts(helper, level, A_EXISTING, 0, 0));
    }

    private static void scenarioAAddFixture(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, A_EXISTING);
        helper.succeedWhen(() -> {
            int countA = markerCount(level, A_EXISTING, MARKER_A);
            helper.assertTrue(countA > 0, "Fixture A was not retro-generated into the pre-mod chunk");
            helper.assertTrue(markerCount(level, A_EXISTING, MARKER_B) == 0,
                    "Fixture B unexpectedly appeared before it was installed");
            ScenarioState.get(level).putInt("a_existing_a", countA);
        });
    }

    private static void scenarioANewChunk(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        prepareCohort(level, A_POST_MOD);
        placeFixture(helper, level, A_POST_MOD, FEATURE_A, 101L);
        int initialCount = markerCount(level, A_POST_MOD, MARKER_A);
        helper.assertTrue(initialCount > 0, "Fixture A did not generate in the post-mod chunk");
        ScenarioState.get(level).putInt("a_post_a", initialCount);
        succeedAfterSettling(helper, () -> assertMarkerCounts(helper, level, A_POST_MOD, initialCount, 0));
    }

    private static void scenarioARestart(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, A_EXISTING);
        loadNeighborhood(level, A_POST_MOD);
        ScenarioState state = ScenarioState.get(level);
        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, A_EXISTING, state.requireInt("a_existing_a"), 0);
            assertMarkerCounts(helper, level, A_POST_MOD, state.requireInt("a_post_a"), 0);
            releaseNeighborhoods(level, A_EXISTING, A_POST_MOD);
        });
    }

    private static void scenarioBExistingWorld(GameTestHelper helper) {
        assertMods(helper, false, false, false);
        ServerLevel level = helper.getLevel();
        level.getLevelData().setGameTime(2_400L);
        prepareCohort(level, B_EXISTING);
        assertMarkerCounts(helper, level, B_EXISTING, 0, 0);
        helper.succeed();
    }

    private static void scenarioBAddOreRenewal(GameTestHelper helper) {
        assertMods(helper, true, false, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, B_EXISTING);
        succeedAfterSettling(helper, () -> assertMarkerCounts(helper, level, B_EXISTING, 0, 0));
    }

    private static void scenarioBAddFixture(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, B_EXISTING);
        helper.succeedWhen(() -> {
            int countA = markerCount(level, B_EXISTING, MARKER_A);
            helper.assertTrue(countA > 0, "Fixture A was not retro-generated into the legacy chunk");
            helper.assertTrue(markerCount(level, B_EXISTING, MARKER_B) == 0,
                    "Fixture B unexpectedly appeared before it was installed");
            ScenarioState.get(level).putInt("b_existing_a", countA);
        });
    }

    private static void scenarioBRestart(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, B_EXISTING);
        int expectedA = ScenarioState.get(level).requireInt("b_existing_a");
        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, B_EXISTING, expectedA, 0);
            releaseNeighborhoods(level, B_EXISTING);
        });
    }

    private static void scenarioCExistingWorld(GameTestHelper helper) {
        assertMods(helper, false, false, false);
        ServerLevel level = helper.getLevel();
        level.getLevelData().setGameTime(2_400L);
        prepareCohort(level, C_BEFORE_A);
        assertMarkerCounts(helper, level, C_BEFORE_A, 0, 0);
        helper.succeed();
    }

    private static void scenarioCAddFixtureA(GameTestHelper helper) {
        assertMods(helper, false, true, false);
        ServerLevel level = helper.getLevel();
        prepareCohort(level, C_WITH_A);
        placeFixture(helper, level, C_WITH_A, FEATURE_A, 201L);
        resetTargetBands(level, C_WITH_A);
        plantPresenceMarker(level, C_WITH_A, MARKER_A, PRESENCE_A_Y);
        int countA = markerCount(level, C_WITH_A, MARKER_A);
        helper.assertTrue(countA > 0, "Fixture A did not generate in its native cohort");
        helper.assertTrue(markerCount(level, C_WITH_A, MARKER_B) == 0,
                "Fixture B appeared before it was installed");
        ScenarioState.get(level).putInt("c_with_a_a_before", countA);
        helper.succeed();
    }

    private static void scenarioCAddFixtureB(GameTestHelper helper) {
        assertMods(helper, false, true, true);
        ServerLevel level = helper.getLevel();
        prepareCohort(level, C_WITH_A_B);
        placeFixture(helper, level, C_WITH_A_B, FEATURE_A, 301L);
        placeFixture(helper, level, C_WITH_A_B, FEATURE_B, 302L);
        resetTargetBands(level, C_WITH_A_B);
        plantPresenceMarker(level, C_WITH_A_B, MARKER_A, PRESENCE_A_Y);
        plantPresenceMarker(level, C_WITH_A_B, MARKER_B, PRESENCE_B_Y);
        int countA = markerCount(level, C_WITH_A_B, MARKER_A);
        int countB = markerCount(level, C_WITH_A_B, MARKER_B);
        helper.assertTrue(countA > 0, "Fixture A did not generate in the A+B cohort");
        helper.assertTrue(countB > 0, "Fixture B did not generate in the A+B cohort");
        ScenarioState state = ScenarioState.get(level);
        state.putInt("c_with_ab_a_before", countA);
        state.putInt("c_with_ab_b_before", countB);
        helper.succeed();
    }

    private static void scenarioCAddOreRenewal(GameTestHelper helper) {
        assertMods(helper, true, true, true);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, C_BEFORE_A);
        loadNeighborhood(level, C_WITH_A);
        loadNeighborhood(level, C_WITH_A_B);
        ScenarioState state = ScenarioState.get(level);

        helper.succeedWhen(() -> {
            int beforeA = markerCount(level, C_BEFORE_A, MARKER_A);
            int beforeB = markerCount(level, C_BEFORE_A, MARKER_B);
            int withAA = markerCount(level, C_WITH_A, MARKER_A);
            int withAB = markerCount(level, C_WITH_A, MARKER_B);
            int withABA = markerCount(level, C_WITH_A_B, MARKER_A);
            int withABB = markerCount(level, C_WITH_A_B, MARKER_B);

            helper.assertTrue(beforeA > 0, "Bootstrap did not add fixture A to the pre-mod cohort");
            helper.assertTrue(beforeB > 0, "Bootstrap did not add fixture B to the pre-mod cohort");
            helper.assertTrue(withAA == state.requireInt("c_with_a_a_before"),
                    "Bootstrap duplicated fixture A in the A-only cohort");
            helper.assertTrue(withAB > 0, "Bootstrap did not add fixture B to the A-only cohort");
            helper.assertTrue(withABA == state.requireInt("c_with_ab_a_before"),
                    "Bootstrap duplicated fixture A in the A+B cohort");
            helper.assertTrue(withABB == state.requireInt("c_with_ab_b_before"),
                    "Bootstrap duplicated fixture B in the A+B cohort");

            state.putInt("c_before_a_after", beforeA);
            state.putInt("c_before_b_after", beforeB);
            state.putInt("c_with_a_a_after", withAA);
            state.putInt("c_with_a_b_after", withAB);
            state.putInt("c_with_ab_a_after", withABA);
            state.putInt("c_with_ab_b_after", withABB);
        });
    }

    private static void scenarioCRestart(GameTestHelper helper) {
        assertMods(helper, true, true, true);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        loadNeighborhood(level, C_BEFORE_A);
        loadNeighborhood(level, C_WITH_A);
        loadNeighborhood(level, C_WITH_A_B);
        ScenarioState state = ScenarioState.get(level);

        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, C_BEFORE_A,
                    state.requireInt("c_before_a_after"), state.requireInt("c_before_b_after"));
            assertMarkerCounts(helper, level, C_WITH_A,
                    state.requireInt("c_with_a_a_after"), state.requireInt("c_with_a_b_after"));
            assertMarkerCounts(helper, level, C_WITH_A_B,
                    state.requireInt("c_with_ab_a_after"), state.requireInt("c_with_ab_b_after"));
            releaseNeighborhoods(level, C_BEFORE_A, C_WITH_A, C_WITH_A_B);
        });
    }

    private static void assertMods(
            GameTestHelper helper,
            boolean oreRenewal,
            boolean fixtureA,
            boolean fixtureB
    ) {
        assertMod(helper, ORE_RENEWAL, oreRenewal);
        assertMod(helper, FIXTURE_A, fixtureA);
        assertMod(helper, FIXTURE_B, fixtureB);
    }

    private static void assertMod(GameTestHelper helper, String modId, boolean expected) {
        helper.assertTrue(ModList.get().isLoaded(modId) == expected,
                "Expected " + modId + " loaded=" + expected);
    }

    private static void accelerateRetrogen() {
        try {
            Class<?> config = Class.forName("dev.synderis.orerenewal.config.OreRenewalConfig");
            setConfigValue(config, "ONLY_WHEN_TICK_HAS_TIME", false);
            setConfigValue(config, "CHUNKS_PER_TICK", 64);
            setConfigValue(config, "LOG_EVERY_N_CHUNKS", 0);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not configure Ore Renewal for deterministic tests: " + exception);
        }
    }

    private static void setConfigValue(Class<?> owner, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getField(fieldName);
        Object configValue = field.get(null);
        if (configValue instanceof ModConfigSpec.ConfigValue<?> typedValue) {
            @SuppressWarnings("unchecked")
            ModConfigSpec.ConfigValue<Object> writableValue = (ModConfigSpec.ConfigValue<Object>) typedValue;
            writableValue.set(value);
        } else {
            throw new IllegalStateException(fieldName + " is not a config value");
        }
    }

    private static void prepareCohort(ServerLevel level, ChunkPos center) {
        persistNeighborhood(level, center);
        resetTargetBands(level, center);
    }

    private static void resetTargetBands(ServerLevel level, ChunkPos center) {
        LevelChunk chunk = level.getChunk(center.x, center.z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getMinBlockX(); x <= center.getMaxBlockX(); x++) {
            for (int z = center.getMinBlockZ(); z <= center.getMaxBlockZ(); z++) {
                fillStone(level, cursor, x, z, TARGET_A_MIN_Y, TARGET_A_MAX_Y);
                fillStone(level, cursor, x, z, TARGET_B_MIN_Y, TARGET_B_MAX_Y);
            }
        }
        chunk.setUnsaved(true);
    }

    private static void fillStone(
            ServerLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int minY,
            int maxY
    ) {
        for (int y = minY; y <= maxY; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, Blocks.STONE.defaultBlockState(), SET_BLOCK_FLAGS);
        }
    }

    private static void plantPresenceMarker(ServerLevel level, ChunkPos center, Block marker, int y) {
        BlockPos pos = new BlockPos(center.getMinBlockX() + 8, y, center.getMinBlockZ() + 8);
        level.setBlock(pos, marker.defaultBlockState(), SET_BLOCK_FLAGS);
    }

    private static void placeFixture(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos center,
            ResourceLocation featureId,
            long seed
    ) {
        Registry<PlacedFeature> registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, featureId);
        Holder<PlacedFeature> holder = registry.getHolder(key).orElseThrow(() ->
                new GameTestAssertException("Missing fixture placed feature " + featureId));
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
        BlockPos origin = new BlockPos(center.getMinBlockX(), level.getMinBuildHeight(), center.getMinBlockZ());
        boolean placed = holder.value().placeWithBiomeCheck(
                level, level.getChunkSource().getGenerator(), random, origin);
        helper.assertTrue(placed, "Fixture placed feature returned false: " + featureId);
    }

    private static int markerCount(ServerLevel level, ChunkPos pos, Block marker) {
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        int count = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.maybeHas(state -> state.is(marker))) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).is(marker)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static void assertMarkerCounts(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos pos,
            int expectedA,
            int expectedB
    ) {
        helper.assertTrue(markerCount(level, pos, MARKER_A) == expectedA,
                "Unexpected fixture A marker count in " + pos);
        helper.assertTrue(markerCount(level, pos, MARKER_B) == expectedB,
                "Unexpected fixture B marker count in " + pos);
    }

    private static void persistNeighborhood(ServerLevel level, ChunkPos center) {
        setNeighborhoodForced(level, center, true);
        loadNeighborhood(level, center);
    }

    private static void loadNeighborhood(ServerLevel level, ChunkPos center) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                level.getChunk(center.x + offsetX, center.z + offsetZ);
            }
        }
    }

    private static void releaseNeighborhoods(ServerLevel level, ChunkPos... centers) {
        for (ChunkPos center : centers) {
            setNeighborhoodForced(level, center, false);
        }
    }

    private static void setNeighborhoodForced(ServerLevel level, ChunkPos center, boolean forced) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                level.setChunkForced(center.x + offsetX, center.z + offsetZ, forced);
            }
        }
    }

    private static void succeedAfterSettling(GameTestHelper helper, Runnable assertions) {
        helper.runAtTickTime(SETTLE_TICKS, () -> {
            assertions.run();
            helper.succeed();
        });
    }
}
