package dev.synderis.orerenewal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.synderis.orerenewal.OreRenewal;
import dev.synderis.orerenewal.world.RetrogenManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class OreRenewalCommands {
    private OreRenewalCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(build("ore_renewal"));
        dispatcher.register(build("orerenewal"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("apply")
                        .then(Commands.argument("feature", ResourceLocationArgument.id())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggestResource(
                                                OreRenewal.RETROGEN.currentFeatures(), builder))
                                .executes(context -> apply(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "feature")))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Ore Renewal: " + OreRenewal.RETROGEN.queuedChunkCount() + " chunks queued, "
                        + OreRenewal.RETROGEN.processedChunkCount() + " processed, "
                        + OreRenewal.RETROGEN.featureRunCount() + " feature runs, "
                        + OreRenewal.RETROGEN.successfulPlacementCount()
                        + " successful placements this session."), false);

        for (RetrogenManager.LevelStatus status : OreRenewal.RETROGEN.status(source.getServer())) {
            source.sendSuccess(() -> Component.literal(
                    status.dimension() + ": revision " + status.revision()
                            + ", " + status.knownFeatures() + " known underground-ore features, "
                            + status.migrations() + " migrations."), false);
        }
        return 1;
    }

    private static int apply(CommandSourceStack source, ResourceLocation featureId) {
        int dimensions = OreRenewal.RETROGEN.forceFeature(source.getServer(), featureId);
        if (dimensions == 0) {
            source.sendFailure(Component.literal(
                    featureId + " is not detected as an ore-generation feature in any loaded dimension."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Queued " + featureId + " for existing chunks in " + dimensions
                        + " dimension(s). New chunks are automatically excluded."), true);
        return dimensions;
    }
}
