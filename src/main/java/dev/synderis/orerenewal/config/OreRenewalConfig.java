package dev.synderis.orerenewal.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OreRenewalConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ONLY_WHEN_TICK_HAS_TIME;
    public static final ModConfigSpec.IntValue LOG_EVERY_N_CHUNKS;
    public static final ModConfigSpec.BooleanValue INCLUDE_NONSTANDARD_UNDERGROUND_FEATURES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("retrogen");
        ENABLED = builder
                .comment("Master switch for applying queued ore features. Detection and history tracking remain active.")
                .define("enabled", true);
        CHUNKS_PER_TICK = builder
                .comment("Maximum existing chunks processed per server tick. Keep this low on large modpacks.")
                .defineInRange("chunks_per_tick", 1, 1, 64);
        ONLY_WHEN_TICK_HAS_TIME = builder
                .comment("Skip retrogen work when the server reports that the current tick is already busy.")
                .define("only_when_tick_has_time", true);
        INCLUDE_NONSTANDARD_UNDERGROUND_FEATURES = builder
                .comment(
                        "Also detect nonstandard configured features placed in UNDERGROUND_ORES.",
                        "Disabled by default because an arbitrary custom feature is not guaranteed to preserve builds.")
                .define("include_nonstandard_underground_features", false);
        LOG_EVERY_N_CHUNKS = builder
                .comment("Write a progress line after this many processed chunks. Set to 0 to disable progress lines.")
                .defineInRange("log_every_n_chunks", 100, 0, 100_000);
        builder.pop();

        SPEC = builder.build();
    }

    private OreRenewalConfig() {
    }
}
