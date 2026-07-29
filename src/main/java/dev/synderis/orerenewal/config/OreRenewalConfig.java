package dev.synderis.orerenewal.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class OreRenewalConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue BOOTSTRAP_ESTABLISHED_WORLDS;
    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ONLY_WHEN_TICK_HAS_TIME;
    public static final ModConfigSpec.IntValue LOG_EVERY_N_CHUNKS;
    public static final ModConfigSpec.BooleanValue INCLUDE_NONSTANDARD_UNDERGROUND_FEATURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_SAFE_ORE_FEATURES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("retrogen");
        ENABLED = builder
                .comment("Master switch for applying queued ore features. Detection and history tracking remain active.")
                .define("enabled", true);
        BOOTSTRAP_ESTABLISHED_WORLDS = builder
                .comment(
                        "On the first Ore Renewal launch in an established world, conservatively recover detected",
                        "non-vanilla ores. Per chunk, a historical feature is skipped when its ore is already present.",
                        "New worlds and later exact feature additions do not use this inference.")
                .define("bootstrap_established_worlds", true);
        CHUNKS_PER_TICK = builder
                .comment("Maximum existing chunks processed per server tick. Keep this low on large modpacks.")
                .defineInRange("chunks_per_tick", 1, 1, 64);
        ONLY_WHEN_TICK_HAS_TIME = builder
                .comment(
                        "Prefer retrogen work only when the server reports spare tick time.",
                        "To prevent permanent queue starvation, one chunk is still attempted after 20 deferred ticks.")
                .define("only_when_tick_has_time", true);
        INCLUDE_NONSTANDARD_UNDERGROUND_FEATURES = builder
                .comment(
                        "Also detect nonstandard configured features placed in UNDERGROUND_ORES.",
                        "Disabled by default because an arbitrary custom feature is not guaranteed to preserve builds.")
                .define("include_nonstandard_underground_features", false);
        ADDITIONAL_SAFE_ORE_FEATURES = builder
                .comment(
                        "Exact placed-feature IDs to treat as ores when their output block is not tagged or named as one.",
                        "Only standard ORE/SCATTERED_ORE generators (and explicitly supported custom generators) qualify.")
                .defineListAllowEmpty(
                        "additional_safe_ore_features",
                        List.of(),
                        () -> "example:placed_ore_feature",
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        LOG_EVERY_N_CHUNKS = builder
                .comment("Write a progress line after this many processed chunks. Set to 0 to disable progress lines.")
                .defineInRange("log_every_n_chunks", 100, 0, 100_000);
        builder.pop();

        SPEC = builder.build();
    }

    private OreRenewalConfig() {
    }
}
