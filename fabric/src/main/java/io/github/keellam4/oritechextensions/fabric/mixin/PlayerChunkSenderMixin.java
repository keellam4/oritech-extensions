package io.github.keellam4.oritechextensions.fabric.mixin;

import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric has no "chunk sent to a player" event. After a chunk reaches a player, send the colours of the painted
 * blocks in it (NeoForge does this in ChunkWatchEvent.Sent).
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void oritechExtensions$afterChunkSent(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        PaintEvents.onChunkSent(connection.player, chunk);
        PaintTargets.onChunkSent(connection.player, level, chunk.getPos());
    }
}
