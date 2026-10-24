package com.ponte.mcunity;

import com.ponte.mcunity.config.BridgeConfig;
import com.ponte.mcunity.server.BridgeServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod principal - inicializa servidor da ponte.
 * <p>
 * A ponte mantém dois servidores:
 * - TCP puro (JSON Lines) na porta 25566 - para Unity desktop
 * - WebSocket na porta 25565 - para Unity WebGL / ferramentas externas
 * Ambos falam o mesmo protocolo.
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
        LOGGER.info("🌉 [Ponte] Minecraft Unity Ponte v1.0.0 carregando...");
        LOGGER.info("[Ponte] Config: {}", config);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.minecraftServer = server;
            startBridge();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stopBridge();
            this.minecraftServer = null;
        });

        // Quando player entra, envia sync inicial de chunks
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (bridgeServer != null) {
                // Delay 1 tick para garantir que o player está no mundo
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

        LOGGER.info("[Ponte] Eventos registrados. Servidor iniciará ao abrir mundo.");
    }

    private void startBridge() {
        if (bridgeServer != null) stopBridge();
        try {
            bridgeServer = new BridgeServer(minecraftServer, config);
            bridgeServer.start();
            LOGGER.info("✅ [Ponte] Ponte ativa! TCP:{} WS:{} | Conecte a Unity agora.", config.tcpPort, config.webSocketPort);
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

    public static MinecraftUnityBridgeMod getInstance() {
        return INSTANCE;
    }

    public BridgeServer getBridgeServer() {
        return bridgeServer;
    }

    public BridgeConfig getConfig() {
        return config;
    }

    public MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }

    /**
     * Chamado pelo Mixin quando um bloco muda no ServerWorld.
     * Rota de alta performance - não pode bloquear thread do jogo.
     */
    public static void onBlockChanged(int x, int y, int z, String blockId) {
        MinecraftUnityBridgeMod inst = INSTANCE;
        if (inst == null || inst.bridgeServer == null) return;
        // Coalesce via queue para não spammar
        inst.bridgeServer.enqueueBlockUpdate(x, y, z, blockId);
    }
}
