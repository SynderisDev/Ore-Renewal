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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

@GameTestHolder("ore_renewal")
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
    private static final Block TARGET_A = Blocks.POLISHED_ANDESITE;
    private static final Block TARGET_B = Blocks.POLISHED_DIORITE;

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
    private static final int FIXTURE_A_PLACEMENT_Y = 16;
    private static final int FIXTURE_B_PLACEMENT_Y = 40;
    private static final int PRESENCE_A_Y = 70;
    private static final int PRESENCE_B_Y = 71;
    private static final int SET_BLOCK_FLAGS = 2;
    private static final int TARGET_MARGIN = 5;
    private static final int SETTLE_TICKS = 80;
    private static final int SAFETY_X_OFFSET = 8;
    private static final int SAFETY_Z_OFFSET = 8;

    private LifecycleScenarioGameTests() {
    }

    @GameTest(templateNamespace = ORE_RENEWAL, template = "empty3x3x3", timeoutTicks = 600)
    public static void lifecyclePhase(GameTestHelper helper) {
        String phase = System.getProperty(PHASE_PROPERTY, "");
        switch (phase) {
            case "A0" -> scenarioAInitial(helper);
            case "A1" -> scenarioAAddFixture(helper);
            case "A2" -> scenarioANewChunk(helper);
            case "A3" -> scenarioAAddFixtureB(helper);
            case "A4" -> scenarioARestart(helper);
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
        plantSafetyMarkers(level, A_EXISTING);
        assertChunkAtCurrentProfileRevision(helper, level, A_EXISTING);
        assertMarkerCounts(helper, level, A_EXISTING, 0, 0);
        assertSafetyMarkers(helper, level, A_EXISTING);
        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, A_EXISTING, 0, 0);
            assertSafetyMarkers(helper, level, A_EXISTING);
            assertChunkAtCurrentProfileRevision(helper, level, A_EXISTING);
            ScenarioState.get(level).putInt("a_phase", 0);
        });
    }

    private static void scenarioAAddFixture(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "a_phase", 0);
        persistNeighborhood(level, A_EXISTING);
        assertFixtureRegisteredForChunk(helper, level, A_EXISTING, FEATURE_A);
        helper.succeedWhen(() -> {
            int countA = markerCount(level, A_EXISTING, MARKER_A);
            helper.assertTrue(countA > 0,
                    "Fixture A was not retro-generated into the pre-mod chunk; controlled targets="
                            + markerCount(level, A_EXISTING, TARGET_A) + "; "
                            + retrogenDiagnostics(level, A_EXISTING, FEATURE_A));
            helper.assertTrue(markerCount(level, A_EXISTING, MARKER_B) == 0,
                    "Fixture B unexpectedly appeared before it was installed");
            assertChunkAtCurrentProfileRevision(helper, level, A_EXISTING);
            assertSafetyMarkers(helper, level, A_EXISTING);
            ScenarioState.get(level).putInt("a_existing_a", countA);
            ScenarioState.get(level).putInt("a_phase", 1);
            recordSuccessfulPhase(level);
        });
    }

    private static void scenarioANewChunk(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "a_phase", 1);
        prepareCenterOnly(level, A_POST_MOD);
        placeFixture(helper, level, A_POST_MOD, FEATURE_A, 101L);
        int initialCount = markerCount(level, A_POST_MOD, MARKER_A);
        helper.assertTrue(initialCount > 0, "Fixture A did not generate in the post-mod chunk");
        assertChunkAtCurrentProfileRevision(helper, level, A_POST_MOD);
        ScenarioState state = ScenarioState.get(level);
        state.putInt("a_post_a", initialCount);
        state.putInt("a_phase", 2);
        saveAndSucceed(helper, level);
    }

    private static void scenarioAAddFixtureB(GameTestHelper helper) {
        assertMods(helper, true, true, true);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "a_phase", 2);
        persistNeighborhood(level, A_EXISTING);
        persistNeighborhood(level, A_POST_MOD);
        assertFixtureRegisteredForChunk(helper, level, A_EXISTING, FEATURE_B);
        assertFixtureRegisteredForChunk(helper, level, A_POST_MOD, FEATURE_B);
        ScenarioState state = ScenarioState.get(level);
        int expectedExistingA = state.requireInt("a_existing_a");
        int expectedPostA = state.requireInt("a_post_a");

        helper.succeedWhen(() -> {
            int existingB = markerCount(level, A_EXISTING, MARKER_B);
            int postB = markerCount(level, A_POST_MOD, MARKER_B);
            helper.assertTrue(existingB > 0, "Fixture B was not added to the pre-A chunk");
            helper.assertTrue(postB > 0, "Fixture B was not added to the post-A new chunk");
            helper.assertTrue(markerCount(level, A_EXISTING, MARKER_A) == expectedExistingA,
                    "Adding fixture B changed fixture A in the pre-A chunk");
            helper.assertTrue(markerCount(level, A_POST_MOD, MARKER_A) == expectedPostA,
                    "The post-A new chunk received duplicate fixture A");
            assertSafetyMarkers(helper, level, A_EXISTING);
            assertChunkAtCurrentProfileRevision(helper, level, A_EXISTING);
            assertChunkAtCurrentProfileRevision(helper, level, A_POST_MOD);
            state.putInt("a_existing_b", existingB);
            state.putInt("a_post_b", postB);
            state.putInt("a_phase", 3);
            recordSuccessfulPhase(level);
        });
    }

    private static void scenarioARestart(GameTestHelper helper) {
        assertMods(helper, true, true, true);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "a_phase", 3);
        persistNeighborhood(level, A_EXISTING);
        persistNeighborhood(level, A_POST_MOD);
        ScenarioState state = ScenarioState.get(level);
        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, A_EXISTING,
                    state.requireInt("a_existing_a"), state.requireInt("a_existing_b"));
            assertMarkerCounts(helper, level, A_POST_MOD,
                    state.requireInt("a_post_a"), state.requireInt("a_post_b"));
            assertChunkAtCurrentProfileRevision(helper, level, A_EXISTING);
            assertChunkAtCurrentProfileRevision(helper, level, A_POST_MOD);
            state.putInt("a_phase", 4);
            releaseNeighborhoods(level, A_EXISTING, A_POST_MOD);
        });
    }

    private static void scenarioBExistingWorld(GameTestHelper helper) {
        assertMods(helper, false, false, false);
        ServerLevel level = helper.getLevel();
        prepareCohort(level, B_EXISTING);
        assertMarkerCounts(helper, level, B_EXISTING, 0, 0);
        ScenarioState.get(level).putInt("b_phase", 0);
        saveAndSucceed(helper, level);
    }

    private static void scenarioBAddOreRenewal(GameTestHelper helper) {
        assertMods(helper, true, false, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "b_phase", 0);
        persistNeighborhood(level, B_EXISTING);
        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, B_EXISTING, 0, 0);
            ScenarioState.get(level).putInt("b_phase", 1);
        });
    }

    private static void scenarioBAddFixture(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "b_phase", 1);
        persistNeighborhood(level, B_EXISTING);
        assertFixtureRegisteredForChunk(helper, level, B_EXISTING, FEATURE_A);
        helper.succeedWhen(() -> {
            int countA = markerCount(level, B_EXISTING, MARKER_A);
            helper.assertTrue(countA > 0, "Fixture A was not retro-generated into the legacy chunk");
            helper.assertTrue(markerCount(level, B_EXISTING, MARKER_B) == 0,
                    "Fixture B unexpectedly appeared before it was installed");
            assertChunkAtCurrentProfileRevision(helper, level, B_EXISTING);
            ScenarioState.get(level).putInt("b_existing_a", countA);
            ScenarioState.get(level).putInt("b_phase", 2);
            recordSuccessfulPhase(level);
        });
    }

    private static void scenarioBRestart(GameTestHelper helper) {
        assertMods(helper, true, true, false);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "b_phase", 2);
        persistNeighborhood(level, B_EXISTING);
        int expectedA = ScenarioState.get(level).requireInt("b_existing_a");
        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, B_EXISTING, expectedA, 0);
            assertChunkAtCurrentProfileRevision(helper, level, B_EXISTING);
            ScenarioState.get(level).putInt("b_phase", 3);
            releaseNeighborhoods(level, B_EXISTING);
        });
    }

    private static void scenarioCExistingWorld(GameTestHelper helper) {
        assertMods(helper, false, false, false);
        ServerLevel level = helper.getLevel();
        prepareCohort(level, C_BEFORE_A);
        assertMarkerCounts(helper, level, C_BEFORE_A, 0, 0);
        ScenarioState.get(level).putInt("c_phase", 0);
        saveAndSucceed(helper, level);
    }

    private static void scenarioCAddFixtureA(GameTestHelper helper) {
        assertMods(helper, false, true, false);
        ServerLevel level = helper.getLevel();
        requirePhase(level, "c_phase", 0);
        prepareCohort(level, C_WITH_A);
        placeFixture(helper, level, C_WITH_A, FEATURE_A, 201L);
        resetTargetBands(level, C_WITH_A);
        plantPresenceMarker(level, C_WITH_A, MARKER_A, PRESENCE_A_Y);
        int countA = markerCount(level, C_WITH_A, MARKER_A);
        helper.assertTrue(countA > 0, "Fixture A did not generate in its native cohort");
        helper.assertTrue(markerCount(level, C_WITH_A, MARKER_B) == 0,
                "Fixture B appeared before it was installed");
        ScenarioState.get(level).putInt("c_with_a_a_before", countA);
        ScenarioState.get(level).putInt("c_phase", 1);
        saveAndSucceed(helper, level);
    }

    private static void scenarioCAddFixtureB(GameTestHelper helper) {
        assertMods(helper, false, true, true);
        ServerLevel level = helper.getLevel();
        requirePhase(level, "c_phase", 1);
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
        state.putInt("c_phase", 2);
        saveAndSucceed(helper, level);
    }

    private static void scenarioCAddOreRenewal(GameTestHelper helper) {
        assertMods(helper, true, true, true);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "c_phase", 2);
        persistNeighborhood(level, C_BEFORE_A);
        persistNeighborhood(level, C_WITH_A);
        persistNeighborhood(level, C_WITH_A_B);
        for (ChunkPos cohort : List.of(C_BEFORE_A, C_WITH_A, C_WITH_A_B)) {
            assertFixtureRegisteredForChunk(helper, level, cohort, FEATURE_A);
            assertFixtureRegisteredForChunk(helper, level, cohort, FEATURE_B);
        }
        ScenarioState state = ScenarioState.get(level);

        helper.succeedWhen(() -> {
            int beforeA = markerCount(level, C_BEFORE_A, MARKER_A);
            int beforeB = markerCount(level, C_BEFORE_A, MARKER_B);
            int withAA = markerCount(level, C_WITH_A, MARKER_A);
            int withAB = markerCount(level, C_WITH_A, MARKER_B);
            int withABA = markerCount(level, C_WITH_A_B, MARKER_A);
            int withABB = markerCount(level, C_WITH_A_B, MARKER_B);

            helper.assertTrue(beforeA > 0,
                    "Bootstrap did not add fixture A to the pre-mod cohort; "
                            + retrogenDiagnostics(level, C_BEFORE_A, FEATURE_A));
            helper.assertTrue(beforeB > 0,
                    "Bootstrap did not add fixture B to the pre-mod cohort; "
                            + retrogenDiagnostics(level, C_BEFORE_A, FEATURE_B));
            helper.assertTrue(withAA == state.requireInt("c_with_a_a_before"),
                    "Bootstrap duplicated fixture A in the A-only cohort");
            helper.assertTrue(withAB > 0, "Bootstrap did not add fixture B to the A-only cohort");
            helper.assertTrue(withABA == state.requireInt("c_with_ab_a_before"),
                    "Bootstrap duplicated fixture A in the A+B cohort");
            helper.assertTrue(withABB == state.requireInt("c_with_ab_b_before"),
                    "Bootstrap duplicated fixture B in the A+B cohort");
            assertChunkAtCurrentProfileRevision(helper, level, C_BEFORE_A);
            assertChunkAtCurrentProfileRevision(helper, level, C_WITH_A);
            assertChunkAtCurrentProfileRevision(helper, level, C_WITH_A_B);

            state.putInt("c_before_a_after", beforeA);
            state.putInt("c_before_b_after", beforeB);
            state.putInt("c_with_a_a_after", withAA);
            state.putInt("c_with_a_b_after", withAB);
            state.putInt("c_with_ab_a_after", withABA);
            state.putInt("c_with_ab_b_after", withABB);
            state.putInt("c_phase", 3);
            recordSuccessfulPhase(level);
        });
    }

    private static void scenarioCRestart(GameTestHelper helper) {
        assertMods(helper, true, true, true);
        accelerateRetrogen();
        ServerLevel level = helper.getLevel();
        requirePhase(level, "c_phase", 3);
        persistNeighborhood(level, C_BEFORE_A);
        persistNeighborhood(level, C_WITH_A);
        persistNeighborhood(level, C_WITH_A_B);
        ScenarioState state = ScenarioState.get(level);

        succeedAfterSettling(helper, () -> {
            assertMarkerCounts(helper, level, C_BEFORE_A,
                    state.requireInt("c_before_a_after"), state.requireInt("c_before_b_after"));
            assertMarkerCounts(helper, level, C_WITH_A,
                    state.requireInt("c_with_a_a_after"), state.requireInt("c_with_a_b_after"));
            assertMarkerCounts(helper, level, C_WITH_A_B,
                    state.requireInt("c_with_ab_a_after"), state.requireInt("c_with_ab_b_after"));
            assertChunkAtCurrentProfileRevision(helper, level, C_BEFORE_A);
            assertChunkAtCurrentProfileRevision(helper, level, C_WITH_A);
            assertChunkAtCurrentProfileRevision(helper, level, C_WITH_A_B);
            state.putInt("c_phase", 4);
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

    private static void requirePhase(ServerLevel level, String key, int expected) {
        int actual = ScenarioState.get(level).requireInt(key);
        if (actual != expected) {
            throw new GameTestAssertException(
                    "Expected persisted " + key + "=" + expected + " but found " + actual);
        }
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

    private static void prepareCenterOnly(ServerLevel level, ChunkPos center) {
        level.setChunkForced(center.x, center.z, true);
        level.getChunk(center.x, center.z);
        resetTargetBands(level, center);
    }

    private static void resetTargetBands(ServerLevel level, ChunkPos center) {
        LevelChunk chunk = level.getChunk(center.x, center.z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getMinBlockX() + TARGET_MARGIN;
             x <= center.getMaxBlockX() - TARGET_MARGIN; x++) {
            for (int z = center.getMinBlockZ() + TARGET_MARGIN;
                 z <= center.getMaxBlockZ() - TARGET_MARGIN; z++) {
                fillTarget(level, cursor, x, z, TARGET_A_MIN_Y, TARGET_A_MAX_Y, TARGET_A);
                fillTarget(level, cursor, x, z, TARGET_B_MIN_Y, TARGET_B_MAX_Y, TARGET_B);
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
                Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.WORLD_SURFACE_WG));
        chunk.setUnsaved(true);
    }

    private static void fillTarget(
            ServerLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int minY,
            int maxY,
            Block target
    ) {
        for (int y = minY; y <= maxY; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, target.defaultBlockState(), SET_BLOCK_FLAGS);
        }
    }

    private static void plantPresenceMarker(ServerLevel level, ChunkPos center, Block marker, int y) {
        BlockPos pos = new BlockPos(center.getMinBlockX() + 8, y, center.getMinBlockZ() + 8);
        level.setBlock(pos, marker.defaultBlockState(), SET_BLOCK_FLAGS);
    }

    private static void plantSafetyMarkers(ServerLevel level, ChunkPos center) {
        level.setBlock(safetyPos(center, 60), Blocks.GLASS.defaultBlockState(), SET_BLOCK_FLAGS);
        level.setBlock(safetyPos(center, 61), Blocks.CRAFTING_TABLE.defaultBlockState(), SET_BLOCK_FLAGS);
        level.setBlock(safetyPos(center, 62), Blocks.CHEST.defaultBlockState(), SET_BLOCK_FLAGS);
        level.setBlock(safetyPos(center, 63), Blocks.AIR.defaultBlockState(), SET_BLOCK_FLAGS);
        if (!(level.getBlockEntity(safetyPos(center, 62)) instanceof ChestBlockEntity chest)) {
            throw new GameTestAssertException("Could not create the lifecycle safety chest");
        }
        chest.setItem(0, new ItemStack(Items.DIAMOND));
        chest.setChanged();
    }

    private static void assertSafetyMarkers(GameTestHelper helper, ServerLevel level, ChunkPos center) {
        helper.assertTrue(level.getBlockState(safetyPos(center, 60)).is(Blocks.GLASS),
                "Retrogen changed the glass safety marker");
        helper.assertTrue(level.getBlockState(safetyPos(center, 61)).is(Blocks.CRAFTING_TABLE),
                "Retrogen changed the crafting-table safety marker");
        helper.assertTrue(level.getBlockState(safetyPos(center, 62)).is(Blocks.CHEST),
                "Retrogen changed the chest safety marker");
        helper.assertTrue(level.getBlockState(safetyPos(center, 63)).isAir(),
                "Retrogen filled the mined-air safety marker");
        helper.assertTrue(level.getBlockEntity(safetyPos(center, 62)) instanceof ChestBlockEntity,
                "Retrogen removed the safety chest block entity");
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(safetyPos(center, 62));
        helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 1,
                "Retrogen changed the safety chest contents");
    }

    private static BlockPos safetyPos(ChunkPos center, int y) {
        return new BlockPos(
                center.getMinBlockX() + SAFETY_X_OFFSET,
                y,
                center.getMinBlockZ() + SAFETY_Z_OFFSET);
    }

    private static void placeFixture(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos center,
            ResourceLocation featureId,
            long seed
    ) {
        assertFixtureRegisteredForChunk(helper, level, center, featureId);
        Registry<PlacedFeature> registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, featureId);
        Holder<PlacedFeature> holder = registry.getHolder(key).orElseThrow(() ->
                new GameTestAssertException("Missing fixture placed feature " + featureId));
        Block target = featureId.equals(FEATURE_A) ? TARGET_A : TARGET_B;
        int targetCount = markerCount(level, center, target);
        helper.assertTrue(targetCount > 0, "Fixture has no controlled target blocks: " + featureId);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
        BlockPos origin = new BlockPos(center.getMinBlockX(), level.getMinBuildHeight(), center.getMinBlockZ());
        boolean placed = holder.value().placeWithBiomeCheck(
                level, level.getChunkSource().getGenerator(), random, origin);
        helper.assertTrue(placed,
                "Fixture placed feature returned false: " + featureId + "; controlled targets=" + targetCount);
    }

    private static void assertFixtureRegisteredForChunk(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos pos,
        ResourceLocation featureId
    ) {
        Set<Holder<Biome>> biomes = new LinkedHashSet<>();
        int placementY = featureId.equals(FEATURE_A)
                ? FIXTURE_A_PLACEMENT_Y
                : FIXTURE_B_PLACEMENT_Y;
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                biomes.add(level.getBiome(new BlockPos(x, placementY, z)));
            }
        }

        List<String> missingBiomeIds = new java.util.ArrayList<>();
        for (Holder<Biome> biome : biomes) {
            boolean registered = false;
            outer:
            for (var step : biome.value().getGenerationSettings().features()) {
                for (Holder<PlacedFeature> placedFeature : step) {
                    if (placedFeature.unwrapKey()
                            .map(ResourceKey::location)
                            .filter(featureId::equals)
                            .isPresent()) {
                        registered = true;
                        break outer;
                    }
                }
            }
            if (!registered) {
                missingBiomeIds.add(biome.unwrapKey()
                        .map(ResourceKey::location)
                        .map(ResourceLocation::toString)
                        .orElse("<direct>"));
            }
        }

        missingBiomeIds.sort(String::compareTo);
        helper.assertTrue(missingBiomeIds.isEmpty(),
                "Fixture " + featureId + " is absent at placement Y=" + placementY
                        + " from chunk " + pos + " biomes " + missingBiomeIds);
    }

    private static int markerCount(ServerLevel level, ChunkPos pos, Block marker) {
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        int count = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.maybeHas(state -> state.is(marker))) {
                continue;
            }
            for (int x = TARGET_MARGIN; x < 16 - TARGET_MARGIN; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = TARGET_MARGIN; z < 16 - TARGET_MARGIN; z++) {
                        if (section.getBlockState(x, y, z).is(marker)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static String retrogenDiagnostics(
            ServerLevel level,
            ChunkPos pos,
            ResourceLocation featureId
    ) {
        try {
            LevelChunk chunk = level.getChunk(pos.x, pos.z);
            Class<?> attachments = Class.forName("dev.synderis.orerenewal.registry.ModAttachments");
            Object attachmentHolder = attachments.getField("CHUNK_REVISION").get(null);
            if (!(attachmentHolder instanceof Supplier<?> supplier)) {
                throw new IllegalStateException("CHUNK_REVISION is not a deferred attachment holder");
            }
            @SuppressWarnings("unchecked")
            AttachmentType<Integer> attachment = (AttachmentType<Integer>) supplier.get();

            Class<?> profileType = Class.forName("dev.synderis.orerenewal.world.OreProfileSavedData");
            Object profile = profileType.getMethod("get", ServerLevel.class).invoke(null, level);
            int profileRevision = (Integer) profileType.getMethod("revision").invoke(profile);

            Class<?> discoveryType = Class.forName("dev.synderis.orerenewal.world.OreFeatureDiscovery");
            OptionalInt step = (OptionalInt) discoveryType
                    .getMethod("findStepInChunk", LevelChunk.class, ResourceLocation.class)
                    .invoke(null, chunk, featureId);

            Class<?> modType = Class.forName("dev.synderis.orerenewal.OreRenewal");
            Object manager = modType.getField("RETROGEN").get(null);
            Class<?> managerType = manager.getClass();

            int fullNeighbors = 0;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (level.getChunkSource().getChunk(
                            pos.x + offsetX, pos.z + offsetZ, ChunkStatus.FULL, false) instanceof LevelChunk) {
                        fullNeighbors++;
                    }
                }
            }

            return "chunkRevision=" + chunk.getData(attachment)
                    + ", hasRevision=" + chunk.hasData(attachment)
                    + ", profileRevision=" + profileRevision
                    + ", step=" + (step.isPresent() ? step.getAsInt() : "missing")
                    + ", fullNeighbors=" + fullNeighbors + "/9"
                    + ", queued=" + managerType.getMethod("queuedChunkCount").invoke(manager)
                    + ", processed=" + managerType.getMethod("processedChunkCount").invoke(manager)
                    + ", runs=" + managerType.getMethod("featureRunCount").invoke(manager)
                    + ", successful=" + managerType.getMethod("successfulPlacementCount").invoke(manager)
                    + ", skipped=" + managerType.getMethod("skippedExistingFeatureCount").invoke(manager);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "diagnostics unavailable: " + exception;
        }
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

    private static void assertChunkAtCurrentProfileRevision(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos pos
    ) {
        try {
            Class<?> attachments = Class.forName("dev.synderis.orerenewal.registry.ModAttachments");
            Object attachmentHolder = attachments.getField("CHUNK_REVISION").get(null);
            if (!(attachmentHolder instanceof Supplier<?> supplier)) {
                throw new IllegalStateException("CHUNK_REVISION is not a deferred attachment holder");
            }
            @SuppressWarnings("unchecked")
            AttachmentType<Integer> attachment = (AttachmentType<Integer>) supplier.get();
            int chunkRevision = level.getChunk(pos.x, pos.z).getData(attachment);

            Class<?> profileType = Class.forName("dev.synderis.orerenewal.world.OreProfileSavedData");
            Object profile = profileType.getMethod("get", ServerLevel.class).invoke(null, level);
            int profileRevision = (Integer) profileType.getMethod("revision").invoke(profile);
            helper.assertTrue(chunkRevision == profileRevision,
                    "Expected " + pos + " at profile revision " + profileRevision
                            + " but found chunk revision " + chunkRevision);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not inspect Ore Renewal revisions: " + exception);
        }
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
            saveAndSucceed(helper, helper.getLevel());
        });
    }

    private static void saveAndSucceed(GameTestHelper helper, ServerLevel level) {
        recordSuccessfulPhase(level);
        helper.succeed();
    }

    private static void recordSuccessfulPhase(ServerLevel level) {
        saveWorld(level);
        String phase = System.getProperty(PHASE_PROPERTY, "");
        Path completion = Path.of("lifecycle-" + phase + ".complete");
        try {
            Files.writeString(completion, phase, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GameTestAssertException("Could not record lifecycle phase " + phase + ": " + exception);
        }
    }

    private static void saveWorld(ServerLevel level) {
        if (!level.getServer().saveEverything(false, true, true)) {
            throw new GameTestAssertException("The lifecycle world could not be saved");
        }
    }
}
