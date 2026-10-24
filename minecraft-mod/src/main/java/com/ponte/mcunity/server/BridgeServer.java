package com.ponte.mcunity.server;

import com.google.gson.*;
import com.ponte.mcunity.MinecraftUnityBridgeMod;
import com.ponte.mcunity.config.BridgeConfig;
import com.ponte.mcunity.server.actions.UnityActionHandler;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servidor da ponte - mantém TCP e WebSocket.
 * <p>
 * Protocolo: JSON Lines (\n) sobre TCP.
 * WS faz handshake RFC6455 e depois mesma framing.
 */
public class BridgeServer {
    private final MinecraftServer mcServer;
    private final BridgeConfig config;
    private final Gson gson = new GsonBuilder().create();
    private final CopyOnWriteArrayList<BridgeClient> clients = new CopyOnWriteArrayList<>();
    private final BlockingQueue<BlockUpdate> blockQueue = new LinkedBlockingQueue<>(8192);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final UnityActionHandler actionHandler;

    private ServerSocket tcpSocket;
    private ServerSocket wsSocket;
    private Thread tcpAcceptThread;
    private Thread wsAcceptThread;
    private Thread blockDispatcherThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Throttle: coalesce updates por posição dentro de 16ms
    private final ConcurrentHashMap<String, BlockUpdate> pendingUpdates = new ConcurrentHashMap<>();

    public BridgeServer(MinecraftServer mcServer, BridgeConfig config) {
        this.mcServer = mcServer;
        this.config = config;
        this.actionHandler = new UnityActionHandler(mcServer, config);
    }

    public void start() throws IOException {
        running.set(true);

        // TCP puro
        tcpSocket = new ServerSocket();
        tcpSocket.bind(new InetSocketAddress(config.bindAddress, config.tcpPort));
        tcpAcceptThread = new Thread(this::acceptLoopTcp, "Ponte-TCP-Accept");
        tcpAcceptThread.setDaemon(true);
        tcpAcceptThread.start();

        // WebSocket (opcional; se porta ocupada, só loga warning)
        try {
            wsSocket = new ServerSocket();
            wsSocket.bind(new InetSocketAddress(config.bindAddress, config.webSocketPort));
            wsAcceptThread = new Thread(this::acceptLoopWs, "Ponte-WS-Accept");
            wsAcceptThread.setDaemon(true);
            wsAcceptThread.start();
            MinecraftUnityBridgeMod.LOGGER.info("[Ponte] WebSocket escutando em {}:{}", config.bindAddress, config.webSocketPort);
        } catch (IOException e) {
            MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Não pôde abrir WebSocket na porta {}: {}", config.webSocketPort, e.getMessage());
            wsSocket = null;
        }

        // Dispatcher de blocos com coalescing
        blockDispatcherThread = new Thread(this::blockDispatchLoop, "Ponte-Block-Dispatcher");
        blockDispatcherThread.setDaemon(true);
        blockDispatcherThread.start();

        // Timer: envia timeUpdate a cada 1s
        scheduler.scheduleAtFixedRate(this::broadcastTime, 1, 1, TimeUnit.SECONDS);

        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] TCP escutando em {}:{}", config.bindAddress, config.tcpPort);
    }

    public void stop() {
        running.set(false);
        try { if (tcpSocket != null) tcpSocket.close(); } catch (IOException ignored) {}
        try { if (wsSocket != null) wsSocket.close(); } catch (IOException ignored) {}
        scheduler.shutdownNow();
        for (BridgeClient c : clients) c.close();
        clients.clear();
        if (tcpAcceptThread != null) tcpAcceptThread.interrupt();
        if (wsAcceptThread != null) wsAcceptThread.interrupt();
        if (blockDispatcherThread != null) blockDispatcherThread.interrupt();
    }

    // === Accept loops ===

    private void acceptLoopTcp() {
        while (running.get()) {
            try {
                Socket s = tcpSocket.accept();
                if (clients.size() >= config.maxClients) {
                    s.close();
                    MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Cliente rejeitado: limite maxClients");
                    continue;
                }
                BridgeClient client = new BridgeClient(s, false, this);
                clients.add(client);
                client.start();
                MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Cliente TCP conectado: {} (total {})", s.getRemoteSocketAddress(), clients.size());
                sendHello(client);
            } catch (IOException e) {
                if (running.get()) MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Erro accept TCP", e);
            }
        }
    }

    private void acceptLoopWs() {
        while (running.get()) {
            try {
                Socket s = wsSocket.accept();
                // WS handshake será feito dentro do BridgeClient após detectar Upgrade
                // Simplificação: aceita e delega, handshake detectado por header
                BridgeClient client = new BridgeClient(s, true, this);
                clients.add(client);
                client.start();
                MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Cliente WS conectado: {}", s.getRemoteSocketAddress());
                // hello enviado após handshake
            } catch (IOException e) {
                if (running.get()) MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Erro accept WS", e);
            }
        }
    }

    // === Mensagens de saída ===

    private void sendHello(BridgeClient c) {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("version", "1.0");
        hello.addProperty("world", mcServer.getOverworld().getRegistryKey().getValue().toString());
        hello.addProperty("motd", "Ponte Minecraft->Unity ativa");
        c.sendJson(hello);
    }

    public void broadcast(JsonObject msg) {
        // cópia serializada uma vez
        String line = gson.toJson(msg);
        for (BridgeClient c : clients) c.sendLine(line);
    }

    public void onClientDisconnected(BridgeClient c) {
        clients.remove(c);
        MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Cliente desconectado (restam {})", clients.size());
    }

    public void onClientMessage(BridgeClient sender, JsonObject msg) {
        String type = msg.has("type") ? msg.get("type").getAsString() : "";
        if (config.verboseLogging) MinecraftUnityBridgeMod.LOGGER.info("[Ponte] ← {} {}", sender.getRemoteAddress(), msg);
        mcServer.execute(() -> {
            try {
                actionHandler.handle(type, msg, sender, this);
            } catch (Exception e) {
                MinecraftUnityBridgeMod.LOGGER.error("[Ponte] Erro ao processar mensagem {}", msg, e);
                JsonObject err = new JsonObject();
                err.addProperty("type", "error");
                err.addProperty("message", e.getMessage());
                sender.sendJson(err);
            }
        });
    }

    // === Blocos: enqueue + dispatch com coalesce ===

    public void enqueueBlockUpdate(int x, int y, int z, String blockId) {
        // descarta se queue cheia (proteção backpressure)
        BlockUpdate u = new BlockUpdate(x, y, z, blockId);
        String key = x + "," + y + "," + z;
        pendingUpdates.put(key, u);
        // sinaliza dispatcher via queue (deduplicate via pending map)
        blockQueue.offer(u);
    }

    private void blockDispatchLoop() {
        while (running.get()) {
            try {
                blockQueue.poll(50, TimeUnit.MILLISECONDS);
                if (pendingUpdates.isEmpty()) continue;
                // Espera throttle window
                Thread.sleep(config.blockUpdateThrottleMs);
                // drena e envia
                Map<String, BlockUpdate> batch = new HashMap<>(pendingUpdates);
                pendingUpdates.clear();

                if (batch.isEmpty()) continue;

                // Se 1 bloco, envia blockUpdate; se muitos, blockBulk
                if (batch.size() == 1) {
                    BlockUpdate u = batch.values().iterator().next();
                    JsonObject m = new JsonObject();
                    m.addProperty("type", "blockUpdate");
                    m.addProperty("x", u.x);
                    m.addProperty("y", u.y);
                    m.addProperty("z", u.z);
                    m.addProperty("block", u.blockId);
                    broadcast(m);
                } else {
                    JsonObject bulk = new JsonObject();
                    bulk.addProperty("type", "blockBulk");
                    JsonArray arr = new JsonArray();
                    for (BlockUpdate u : batch.values()) {
                        JsonObject o = new JsonObject();
                        o.addProperty("x", u.x);
                        o.addProperty("y", u.y);
                        o.addProperty("z", u.z);
                        o.addProperty("block", u.blockId);
                        arr.add(o);
                    }
                    bulk.add("blocks", arr);
                    broadcast(bulk);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // === Sync inicial ===

    public void sendInitialChunks(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ChunkPos center = player.getChunkPos();
        int r = config.syncRadiusChunks;

        // Envia em batch para não congelar tick: agenda chunks espalhados
        scheduler.execute(() -> {
            try {
                // Primeiro envia dimensões e spawn
                JsonObject info = new JsonObject();
                info.addProperty("type", "worldInfo");
                info.addProperty("dimension", world.getRegistryKey().getValue().toString());
                JsonObject spawn = new JsonObject();
                BlockPos sp = world.getSpawnPos();
                spawn.addProperty("x", sp.getX());
                spawn.addProperty("y", sp.getY());
                spawn.addProperty("z", sp.getZ());
                info.add("spawn", spawn);
                info.addProperty("time", world.getTimeOfDay());
                info.addProperty("raining", world.isRaining());
                info.addProperty("thundering", world.isThundering());
                broadcast(info);

                // Coleta chunks carregados dentro do raio
                List<WorldChunk> chunks = new ArrayList<>();
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                        if (world.isChunkLoaded(cp.x, cp.z)) {
                            WorldChunk ch = world.getChunk(cp.x, cp.z);
                            chunks.add(ch);
                        }
                    }
                }

                // Envia chunkData em batches de ~2000 blocos não-ar por mensagem
                for (WorldChunk ch : chunks) {
                    JsonObject chunkMsg = buildChunkMessage(world, ch);
                    if (chunkMsg != null) {
                        String line = gson.toJson(chunkMsg);
                        for (BridgeClient c : clients) c.sendLine(line);
                        // throttle leve
                        Thread.sleep(10);
                    }
                }

                // sinaliza fim do sync
                JsonObject done = new JsonObject();
                done.addProperty("type", "syncComplete");
                done.addProperty("chunksSent", chunks.size());
                broadcast(done);

                MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Sync inicial: {} chunks enviados para {}", chunks.size(), player.getName().getString());
            } catch (Exception e) {
                MinecraftUnityBridgeMod.LOGGER.error("[Ponte] Erro no sync inicial", e);
            }
        });
    }

    private JsonObject buildChunkMessage(ServerWorld world, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        JsonArray blocks = new JsonArray();
        int count = 0;
        // Varre chunk (16x384x16 na 1.20 = from -64 to 319)
        int minY = world.getBottomY();
        int maxY = world.getTopY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos bp = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                    BlockState state = chunk.getBlockState(bp);
                    if (state.isAir()) continue;
                    String id = state.getRegistryEntry().getKey().orElseThrow().getValue().toString();
                    // skip otimização: só envia blocos visíveis? por enquanto todos não-ar
                    JsonObject b = new JsonObject();
                    b.addProperty("x", bp.getX());
                    b.addProperty("y", bp.getY());
                    b.addProperty("z", bp.getZ());
                    b.addProperty("block", id);
                    blocks.add(b);
                    count++;
                    if (count > 2500) break; // limite por mensagem
                }
                if (count > 2500) break;
            }
            if (count > 2500) break;
        }
        if (count == 0) return null;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "chunkData");
        msg.addProperty("chunkX", pos.x);
        msg.addProperty("chunkZ", pos.z);
        msg.add("blocks", blocks);
        msg.addProperty("count", count);
        return msg;
    }

    public void broadcastPlayerJoin(ServerPlayerEntity player) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "playerJoin");
        m.addProperty("player", player.getName().getString());
        m.addProperty("uuid", player.getUuidAsString());
        m.addProperty("x", player.getX());
        m.addProperty("y", player.getY());
        m.addProperty("z", player.getZ());
        broadcast(m);
    }

    public void broadcastPlayerLeave(ServerPlayerEntity player) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "playerLeave");
        m.addProperty("player", player.getName().getString());
        broadcast(m);
    }

    public void broadcastChat(String message) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "chat");
        m.addProperty("message", message);
        broadcast(m);
    }

    private void broadcastTime() {
        if (mcServer == null || mcServer.getOverworld() == null) return;
        long time = mcServer.getOverworld().getTimeOfDay() % 24000;
        JsonObject m = new JsonObject();
        m.addProperty("type", "timeUpdate");
        m.addProperty("time", time);
        m.addProperty("isDay", time < 12000);
        broadcast(m);
    }

    // Helpers para action handler
    public void sendToAll(JsonObject msg) { broadcast(msg); }
    public Collection<BridgeClient> getClients() { return clients; }
    public MinecraftServer getMcServer() { return mcServer; }

    private static class BlockUpdate {
        final int x, y, z;
        final String blockId;
        BlockUpdate(int x, int y, int z, String blockId) { this.x=x; this.y=y; this.z=z; this.blockId=blockId; }
    }
}
