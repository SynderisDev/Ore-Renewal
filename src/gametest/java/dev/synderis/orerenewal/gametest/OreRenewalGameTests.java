package dev.synderis.orerenewal.gametest;

import dev.synderis.orerenewal.OreRenewal;
import dev.synderis.orerenewal.registry.ModAttachments;
import dev.synderis.orerenewal.world.OreFeatureDiscovery;
import dev.synderis.orerenewal.world.RetrogenManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@GameTestHolder(OreRenewal.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreRenewalGameTests {
    private static final String EMPTY_TEMPLATE = "empty3x3x3";
    private static final String DEDICATED_SERVER_TEST = "dedicatedServerLoadsAndDiscoversVanillaOres";
    private static final String CHUNK_LOAD_TEST = "chunkLoadMarksOnlyNewChunks";
    private static final String COMMAND_SURFACE_TEST = "operatorCommandSurfaceIsRegistered";
    private static final Set<String> REQUIRED_TESTS = Set.of(
            DEDICATED_SERVER_TEST,
            CHUNK_LOAD_TEST,
            COMMAND_SURFACE_TEST
    );
    private static final Set<String> PASSED_TESTS = new HashSet<>();
    private static final Path COMPLETION_MARKER = Path.of("required-gametests.complete");
    private static final String COMPLETION_MARKER_CONTENT = String.join("\n",
            DEDICATED_SERVER_TEST,
            CHUNK_LOAD_TEST,
            COMMAND_SURFACE_TEST
    );

    private OreRenewalGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dedicatedServerLoadsAndDiscoversVanillaOres(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            helper.fail("GameTest must run on a dedicated server, not a client", BlockPos.ZERO);
        }
        if (!ModList.get().isLoaded(OreRenewal.MOD_ID)) {
            helper.fail("Ore Renewal was not loaded by the headless server", BlockPos.ZERO);
        }

        Map<ResourceLocation, Integer> features = OreFeatureDiscovery.discover(helper.getLevel());
        ResourceLocation diamondFeature = features.keySet().stream()
                .filter(id -> ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace()))
                .filter(id -> id.getPath().startsWith("ore_diamond"))
                .findFirst()
                .orElse(null);
        if (diamondFeature == null) {
            helper.fail("Ore discovery did not find a vanilla diamond placed feature", BlockPos.ZERO);
        }

        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        if (OreFeatureDiscovery.findStepInChunk(chunk, diamondFeature).isEmpty()) {
            helper.fail("The discovered diamond feature was not present in the test chunk biome", BlockPos.ZERO);
        }
        recordPassedTest(DEDICATED_SERVER_TEST);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void chunkLoadMarksOnlyNewChunks(GameTestHelper helper) {
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        RetrogenManager manager = new RetrogenManager();

        chunk.setData(ModAttachments.CHUNK_REVISION, 7);
        manager.onChunkLoad(new ChunkEvent.Load(chunk, false));
        if (chunk.getData(ModAttachments.CHUNK_REVISION) != 7) {
            helper.fail("An existing chunk was incorrectly marked as newly generated", BlockPos.ZERO);
        }

        manager.onChunkLoad(new ChunkEvent.Load(chunk, true));
        if (chunk.getData(ModAttachments.CHUNK_REVISION) != -1) {
            helper.fail("A newly generated chunk did not receive the exclusion sentinel", BlockPos.ZERO);
        }
        recordPassedTest(CHUNK_LOAD_TEST);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void operatorCommandSurfaceIsRegistered(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String alias : new String[]{"ore_renewal", "orerenewal"}) {
            var command = root.getChild(alias);
            if (command == null) {
                helper.fail("Missing operator command /" + alias, BlockPos.ZERO);
                return;
            }
            for (String child : new String[]{"status", "checkpoint", "apply", "apply-all-modded"}) {
                if (command.getChild(child) == null) {
                    helper.fail("Missing /" + alias + " " + child, BlockPos.ZERO);
                    return;
                }
            }
        }
        recordPassedTest(COMMAND_SURFACE_TEST);
        helper.succeed();
    }

    private static synchronized void recordPassedTest(String testName) {
        if (!REQUIRED_TESTS.contains(testName)) {
            throw new GameTestAssertException("Unexpected required GameTest name: " + testName);
        }
        PASSED_TESTS.add(testName);
        if (!PASSED_TESTS.equals(REQUIRED_TESTS)) {
            return;
        }

        try {
            Files.writeString(COMPLETION_MARKER, COMPLETION_MARKER_CONTENT, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GameTestAssertException("Could not record required GameTest completion: " + exception);
        }
    }
}
