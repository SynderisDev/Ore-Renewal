package dev.synderis.orerenewal;

import com.mojang.logging.LogUtils;
import dev.synderis.orerenewal.command.OreRenewalCommands;
import dev.synderis.orerenewal.config.OreRenewalConfig;
import dev.synderis.orerenewal.registry.ModAttachments;
import dev.synderis.orerenewal.world.RetrogenManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(OreRenewal.MOD_ID)
public final class OreRenewal {
    public static final String MOD_ID = "ore_renewal";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final RetrogenManager RETROGEN = new RetrogenManager();

    public OreRenewal(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, OreRenewalConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(RETROGEN::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RETROGEN::onServerStopped);
        NeoForge.EVENT_BUS.addListener(RETROGEN::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(RETROGEN::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(RETROGEN::onServerTick);
        NeoForge.EVENT_BUS.addListener(OreRenewalCommands::register);
    }
}
