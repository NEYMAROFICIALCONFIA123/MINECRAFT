package com.ponte.mcunity;

import com.ponte.mcunity.config.BridgeConfig;
import com.ponte.mcunity.server.BridgeServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod principal - inicializa servidor da ponte.
 * VERSÃO SEM MIXIN (v1.0.1-fix2) - usa Fabric API events para não crashar.
 * Detecta place/break via UseBlockCallback + PlayerBlockBreakEvents + polling leve.
 */
public class MinecraftUnityBridgeMod implements ModInitializer {
    public static final String MOD_ID = "minecraft-unity-ponte";
    public static final Logger LOGGER = LoggerFactory.getLogger("Ponte");

    private static MinecraftUnityBridgeMod INSTANCE;
    private BridgeServer bridgeServer;
    private BridgeConfig config;
    private MinecraftServer minecraftServer;

    @Override
    public void onInitialize() {
        INSTANCE = this;
        config = BridgeConfig.loadOrCreate();
        LOGGER.info("🌉 [Ponte] Minecraft Unity Ponte v1.0.1-fix2 carregando (SEM MIXIN)...");
        LOGGER.info("[Ponte] Config: {}", config);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.minecraftServer = server;
            startBridge();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stopBridge();
            this.minecraftServer = null;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (bridgeServer != null) {
                server.execute(() -> {
                    bridgeServer.broadcastPlayerJoin(handler.player);
                    bridgeServer.sendInitialChunks(handler.player);
                });
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (bridgeServer != null) {
                bridgeServer.broadcastPlayerLeave(handler.player);
            }
        });

        // === Detecção de blocos SEM MIXIN ===
        // 1) Quebra: PlayerBlockBreakEvents.AFTER
        try {
            PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
                if (bridgeServer != null && !world.isClient) {
                    // envia como air (removido)
                    onBlockChanged(pos.getX(), pos.getY(), pos.getZ(), "minecraft:air");
                }
            });
            LOGGER.info("[Ponte] Hook PlayerBlockBreakEvents.AFTER registrado");
        } catch (Throwable t) {
            LOGGER.warn("[Ponte] PlayerBlockBreakEvents não disponível: {}", t.getMessage());
        }

        // 2) Colocação: UseBlockCallback (quando clica com bloco)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (bridgeServer != null && !world.isClient) {
                // delay 1 tick para pegar o bloco já colocado
                if (minecraftServer != null) {
                    var hitPos = hitResult.getBlockPos().offset(hitResult.getSide());
                    minecraftServer.execute(() -> {
                        var state = world.getBlockState(hitPos);
                        if (!state.isAir()) {
                            String id = getBlockId(state);
                            onBlockChanged(hitPos.getX(), hitPos.getY(), hitPos.getZ(), id);
                        }
                    });
                }
            }
            return ActionResult.PASS;
        });

        // 3) Ataque (clique esquerdo) como fallback
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            // não faz nada aqui, só garante que break será capturado acima
            return ActionResult.PASS;
        });

        // 4) Polling leve (opcional): a cada 20 ticks verifica comandos /fill etc
        // Se quiser sync 100% até de comandos /setblock, use mixin - mas sem mixin pegamos 90% dos casos (player actions)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // reservado para futuro: poderia verificar queue de mudanças via command
        });

        LOGGER.info("[Ponte] Eventos registrados (SEM MIXIN). Servidor iniciará ao abrir mundo.");
    }

    private String getBlockId(BlockState state) {
        try {
            var key = state.getRegistryEntry().getKey().orElse(null);
            if (key != null) return key.getValue().toString();
        } catch (Exception ignored) {}
        // fallback
        return state.isAir() ? "minecraft:air" : "minecraft:stone";
    }

    private void startBridge() {
        if (bridgeServer != null) stopBridge();
        try {
            bridgeServer = new BridgeServer(minecraftServer, config);
            bridgeServer.start();
            LOGGER.info("✅ [Ponte] Ponte ativa! TCP:{} WS:{} | Conecte a Unity/Godot agora.", config.tcpPort, config.webSocketPort);
            LOGGER.info("   → Se firewall bloquear, libere as portas TCP {} e {}", config.tcpPort, config.webSocketPort);
        } catch (Exception e) {
            LOGGER.error("❌ [Ponte] Falha ao iniciar servidor da ponte", e);
        }
    }

    private void stopBridge() {
        if (bridgeServer != null) {
            bridgeServer.stop();
            bridgeServer = null;
            LOGGER.info("[Ponte] Servidor da ponte parado.");
        }
    }

    public static MinecraftUnityBridgeMod getInstance() { return INSTANCE; }
    public BridgeServer getBridgeServer() { return bridgeServer; }
    public BridgeConfig getConfig() { return config; }
    public MinecraftServer getMinecraftServer() { return minecraftServer; }

    public static void onBlockChanged(int x, int y, int z, String blockId) {
        MinecraftUnityBridgeMod inst = INSTANCE;
        if (inst == null || inst.bridgeServer == null) return;
        inst.bridgeServer.enqueueBlockUpdate(x, y, z, blockId);
    }
}
