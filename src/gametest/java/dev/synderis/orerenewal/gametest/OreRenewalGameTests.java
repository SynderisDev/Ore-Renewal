package dev.synderis.orerenewal.gametest;

import dev.synderis.orerenewal.OreRenewal;
import dev.synderis.orerenewal.registry.ModAttachments;
import dev.synderis.orerenewal.world.OreFeatureDiscovery;
import dev.synderis.orerenewal.world.RetrogenManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(OreRenewal.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreRenewalGameTests {
    private static final String EMPTY_TEMPLATE = "empty3x3x3";

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
        helper.succeed();
    }
}
