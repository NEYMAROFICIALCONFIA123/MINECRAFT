package com.ponte.mcunity.server.actions;

import com.google.gson.JsonObject;
import com.ponte.mcunity.MinecraftUnityBridgeMod;
import com.ponte.mcunity.config.BridgeConfig;
import com.ponte.mcunity.server.BridgeClient;
import com.ponte.mcunity.server.BridgeServer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Processa comandos vindos da Unity e executa no servidor Minecraft.
 * Sempre chamado na main thread via server.execute().
 */
public class UnityActionHandler {
    private final MinecraftServer server;
    private final BridgeConfig config;
    private final ColorMapper colorMapper = new ColorMapper();

    public UnityActionHandler(MinecraftServer server, BridgeConfig config) {
        this.server = server;
        this.config = config;
    }

    public void handle(String type, JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        switch (type) {
            case "setBlock" -> handleSetBlock(msg, sender, bridge);
            case "fill" -> handleFill(msg, sender, bridge);
            case "setTime" -> handleSetTime(msg, sender, bridge);
            case "setWeather" -> handleSetWeather(msg, sender, bridge);
            case "command" -> handleCommand(msg, sender, bridge);
            case "setBlockColor" -> handleSetBlockColor(msg, sender, bridge);
            case "teleport" -> handleTeleport(msg, sender, bridge);
            case "chat" -> handleChat(msg, sender, bridge);
            case "requestChunk" -> handleRequestChunk(msg, sender, bridge);
            case "ping" -> {
                JsonObject pong = new JsonObject();
                pong.addProperty("type", "pong");
                if (msg.has("id")) pong.add("id", msg.get("id"));
                sender.sendJson(pong);
            }
            default -> {
                JsonObject err = new JsonObject();
                err.addProperty("type", "error");
                err.addProperty("message", "Tipo desconhecido: " + type);
                sender.sendJson(err);
            }
        }
    }

    private ServerWorld getWorld() {
        return server.getOverworld(); // por simplicidade, sempre overworld; pode extender para escolher dimension
    }

    private void handleSetBlock(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        int x = msg.get("x").getAsInt();
        int y = msg.get("y").getAsInt();
        int z = msg.get("z").getAsInt();
        String blockId = msg.get("block").getAsString(); // ex: minecraft:stone ou air para remover

        BlockPos pos = new BlockPos(x, y, z);
        if (!isInBounds(pos)) {
            sendError(sender, "Posição fora dos limites do mundo");
            return;
        }

        ServerWorld world = getWorld();
        if ("minecraft:air".equals(blockId) || "air".equals(blockId)) {
            world.breakBlock(pos, false);
        } else {
            Block block = Registries.BLOCK.get(new Identifier(blockId));
            BlockState state = block.getDefaultState();
            world.setBlockState(pos, state);
        }
        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Unity setBlock {} em {},{},{}", blockId, x, y, z);
        sendOk(sender, "setBlock");
    }

    private void handleFill(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        JsonObject from = msg.getAsJsonObject("from");
        JsonObject to = msg.getAsJsonObject("to");
        String blockId = msg.get("block").getAsString();

        int x1 = from.get("x").getAsInt(), y1 = from.get("y").getAsInt(), z1 = from.get("z").getAsInt();
        int x2 = to.get("x").getAsInt(), y2 = to.get("y").getAsInt(), z2 = to.get("z").getAsInt();

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        long volume = (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 10000) {
            sendError(sender, "Volume muito grande: " + volume + " (max 10000)");
            return;
        }

        ServerWorld world = getWorld();
        Block block = null;
        BlockState state = null;
        boolean isAir = "minecraft:air".equals(blockId);
        if (!isAir) {
            block = Registries.BLOCK.get(new Identifier(blockId));
            state = block.getDefaultState();
        }

        int changed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isAir) world.breakBlock(pos, false);
                    else world.setBlockState(pos, state);
                    changed++;
                }
            }
        }
        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Unity fill {} blocos com {} de {} a {}", changed, blockId, minX + "," + minY + "," + minZ, maxX + "," + maxY + "," + maxZ);
        JsonObject ok = new JsonObject();
        ok.addProperty("type", "ok");
        ok.addProperty("action", "fill");
        ok.addProperty("changed", changed);
        sender.sendJson(ok);
    }

    private void handleSetBlockColor(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        int x = msg.get("x").getAsInt();
        int y = msg.get("y").getAsInt();
        int z = msg.get("z").getAsInt();
        String hex = msg.get("color").getAsString(); // #FF0000
        String material = msg.has("material") ? msg.get("material").getAsString() : "concrete"; // concrete/wool/terracotta

        String blockId = colorMapper.closestBlock(hex, material);
        BlockPos pos = new BlockPos(x, y, z);
        ServerWorld world = getWorld();
        Block block = Registries.BLOCK.get(new Identifier(blockId));
        world.setBlockState(pos, block.getDefaultState());

        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Unity pintar {} com {} -> {}", pos, hex, blockId);
        sendOk(sender, "setBlockColor");
    }

    private void handleSetTime(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        if (!config.enableTimeControl) {
            sendError(sender, "Controle de tempo desabilitado na config");
            return;
        }
        long time = msg.get("time").getAsLong(); // 0..24000
        for (ServerWorld w : server.getWorlds()) {
            w.setTimeOfDay(time);
        }
        JsonObject update = new JsonObject();
        update.addProperty("type", "timeUpdate");
        update.addProperty("time", time % 24000);
        bridge.broadcast(update);
        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Unity setTime {}", time);
        sendOk(sender, "setTime");
    }

    private void handleSetWeather(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        if (!config.enableWeatherControl) {
            sendError(sender, "Controle de clima desabilitado");
            return;
        }
        String weather = msg.get("weather").getAsString(); // clear / rain / thunder
        ServerWorld world = getWorld();
        switch (weather) {
            case "clear" -> {
                world.setWeather(6000, 0, false, false);
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), "weather clear");
            }
            case "rain" -> server.getCommandManager().executeWithPrefix(server.getCommandSource(), "weather rain");
            case "thunder" -> server.getCommandManager().executeWithPrefix(server.getCommandSource(), "weather thunder");
            default -> {
                sendError(sender, "Weather inválido: " + weather);
                return;
            }
        }
        JsonObject m = new JsonObject();
        m.addProperty("type", "weatherUpdate");
        m.addProperty("weather", weather);
        bridge.broadcast(m);
        sendOk(sender, "setWeather");
    }

    private void handleCommand(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        if (!config.allowCommands) {
            sendError(sender, "Comandos desabilitados");
            return;
        }
        String cmd = msg.get("command").getAsString();
        // sanitiza: remove barra inicial se houver
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Unity command: {}", cmd);
        sendOk(sender, "command");
    }

    private void handleTeleport(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        if (!config.enableTeleport) {
            sendError(sender, "Teleporte desabilitado");
            return;
        }
        // teleporta o primeiro player online (ou player especificado)
        String targetPlayer = msg.has("player") ? msg.get("player").getAsString() : null;
        double x = msg.get("x").getAsDouble();
        double y = msg.get("y").getAsDouble();
        double z = msg.get("z").getAsDouble();

        ServerPlayerEntity player = null;
        if (targetPlayer != null) player = server.getPlayerManager().getPlayer(targetPlayer);
        else if (!server.getPlayerManager().getPlayerList().isEmpty())
            player = server.getPlayerManager().getPlayerList().get(0);

        if (player == null) {
            sendError(sender, "Nenhum player online para teleportar");
            return;
        }
        player.teleport(getWorld(), x, y, z, player.getYaw(), player.getPitch());
        sendOk(sender, "teleport");
    }

    private void handleChat(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        String text = msg.get("message").getAsString();
        String from = msg.has("from") ? msg.get("from").getAsString() : "Unity";
        // envia para todos os players
        server.getPlayerManager().broadcast(Text.literal("[Unity:" + from + "] " + text), false);
        // e re-broadcast para outros clientes Unity
        JsonObject chat = new JsonObject();
        chat.addProperty("type", "chat");
        chat.addProperty("from", from);
        chat.addProperty("message", text);
        bridge.broadcast(chat);
        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Chat Unity->MC: {}", text);
    }

    private void handleRequestChunk(JsonObject msg, BridgeClient sender, BridgeServer bridge) {
        int cx = msg.get("chunkX").getAsInt();
        int cz = msg.get("chunkZ").getAsInt();
        ServerWorld world = getWorld();
        if (!world.isChunkLoaded(cx, cz)) {
            sendError(sender, "Chunk não carregado: " + cx + "," + cz);
            return;
        }
        // similar ao buildChunkMessage mas envia só para o solicitante
        // simplificado: envia confirmação por enquanto
        JsonObject ack = new JsonObject();
        ack.addProperty("type", "chunkRequested");
        ack.addProperty("chunkX", cx);
        ack.addProperty("chunkZ", cz);
        ack.addProperty("status", "loading_see_bulk");
        sender.sendJson(ack);
        // TODO: enviar dados reais do chunk
    }

    private boolean isInBounds(BlockPos pos) {
        ServerWorld w = getWorld();
        return pos.getY() >= w.getBottomY() && pos.getY() < w.getTopY();
    }

    private void sendOk(BridgeClient c, String action) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "ok");
        m.addProperty("action", action);
        c.sendJson(m);
    }

    private void sendError(BridgeClient c, String msg) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "error");
        m.addProperty("message", msg);
        c.sendJson(m);
    }
}
