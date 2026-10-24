package com.ponte.mcunity.mixin;

import com.ponte.mcunity.MinecraftUnityBridgeMod;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepta mudanças de blocos no ServerWorld para enviar à Unity.
 * Cobre place, break, fill, comandos, etc - tudo passa por setBlockState.
 */
@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("TAIL"))
    private void onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        // Só processa se realmente mudou (return true) - mas enviamos sempre para garantir sync
        try {
            String blockId = state.getRegistryEntry().getKey().orElse(null) != null
                    ? state.getRegistryEntry().getKey().get().getValue().toString()
                    : state.getBlock().toString();
            // Fallback via toString se registry falhar (ex: ar)
            if (blockId == null || blockId.isEmpty() || blockId.contains("@")) {
                // tenta via block id
                blockId = state.isAir() ? "minecraft:air" : "minecraft:stone";
            }
            MinecraftUnityBridgeMod.onBlockChanged(pos.getX(), pos.getY(), pos.getZ(), blockId);
        } catch (Exception e) {
            // nunca quebrar o jogo por causa da ponte
        }
    }
}
