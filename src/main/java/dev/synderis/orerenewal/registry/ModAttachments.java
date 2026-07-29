package dev.synderis.orerenewal.registry;

import com.mojang.serialization.Codec;
import dev.synderis.orerenewal.OreRenewal;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreRenewal.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> CHUNK_REVISION =
            ATTACHMENT_TYPES.register("chunk_revision",
                    () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModAttachments() {
    }
}
