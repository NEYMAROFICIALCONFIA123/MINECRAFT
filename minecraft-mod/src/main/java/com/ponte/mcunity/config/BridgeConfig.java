package com.ponte.mcunity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuração da ponte, persistida em config/minecraft-unity-bridge.json
 */
public class BridgeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Ponte/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int tcpPort = 25566;
    public int webSocketPort = 25565;
    public int syncRadiusChunks = 8;
    public int maxClients = 8;
    public boolean enableTimeControl = true;
    public boolean enableWeatherControl = true;
    public boolean enableTeleport = true;
    public boolean allowCommands = true;
    public boolean requireOpForUnityActions = false;
    public int blockUpdateThrottleMs = 16; // ~60fps coalescing
    public boolean verboseLogging = false;
    public String bindAddress = "0.0.0.0";

    public static BridgeConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("minecraft-unity-bridge.json");
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                BridgeConfig cfg = GSON.fromJson(json, BridgeConfig.class);
                LOGGER.info("[Ponte] Config carregada de {}", configPath);
                return cfg;
            } catch (Exception e) {
                LOGGER.warn("[Ponte] Falha ao ler config, usando padrões", e);
            }
        }
        BridgeConfig cfg = new BridgeConfig();
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(cfg));
            LOGGER.info("[Ponte] Config padrão criada em {}", configPath);
        } catch (IOException e) {
            LOGGER.warn("[Ponte] Não pôde salvar config padrão", e);
        }
        return cfg;
    }

    public void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("minecraft-unity-bridge.json");
        try {
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.error("[Ponte] Erro ao salvar config", e);
        }
    }

    @Override
    public String toString() {
        return "tcp=" + tcpPort + " ws=" + webSocketPort + " radius=" + syncRadiusChunks;
    }
}
