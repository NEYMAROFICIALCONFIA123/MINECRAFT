package com.ponte.mcunity.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ponte.mcunity.MinecraftUnityBridgeMod;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Representa um cliente Unity conectado (TCP ou WS).
 * Thread de leitura + queue de escrita.
 */
public class BridgeClient {
    private final Socket socket;
    private final boolean isWebSocketMode;
    private final BridgeServer server;

    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>(2048);
    private Thread readThread;
    private Thread writeThread;
    private volatile boolean alive = true;
    private boolean wsHandshakeDone = false;
    private InputStream rawIn;
    private OutputStream rawOut;

    public BridgeClient(Socket socket, boolean isWebSocketMode, BridgeServer server) {
        this.socket = socket;
        this.isWebSocketMode = isWebSocketMode;
        this.server = server;
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
        } catch (IOException ignored) {}
    }

    public void start() {
        try {
            rawIn = socket.getInputStream();
            rawOut = socket.getOutputStream();
        } catch (IOException e) {
            close();
            return;
        }

        readThread = new Thread(this::readLoop, "Ponte-Client-Read-" + socket.getRemoteSocketAddress());
        readThread.setDaemon(true);
        readThread.start();

        writeThread = new Thread(this::writeLoop, "Ponte-Client-Write-" + socket.getRemoteSocketAddress());
        writeThread.setDaemon(true);
        writeThread.start();
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress() != null ? socket.getRemoteSocketAddress().toString() : "unknown";
    }

    public void sendJson(JsonObject obj) {
        sendLine(obj.toString());
    }

    public void sendLine(String line) {
        if (!alive) return;
        if (!sendQueue.offer(line)) {
            MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Send queue cheia para {}", getRemoteAddress());
        }
    }

    public void close() {
        alive = false;
        try { socket.close(); } catch (IOException ignored) {}
        if (readThread != null) readThread.interrupt();
        if (writeThread != null) writeThread.interrupt();
        server.onClientDisconnected(this);
    }

    // === Read loop: detecta WS handshake se necessário, senão JSON Lines ===

    private void readLoop() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));
            // Se modo WS, tenta handshake primeiro
            if (isWebSocketMode) {
                // Lê até encontrar linha vazia (fim headers)
                String line;
                String wsKey = null;
                StringBuilder headers = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    headers.append(line).append("\n");
                    if (line.isEmpty()) break;
                    if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                        wsKey = line.substring(line.indexOf(":") + 1).trim();
                    }
                    if (line.startsWith("GET ")) {
                        // confirma que é upgrade
                    }
                }
                if (wsKey != null) {
                    doWsHandshake(wsKey);
                    wsHandshakeDone = true;
                    // Agora leitura é via frames WS
                    readWsFrames();
                    return;
                } else {
                    // Não era WS, trata como TCP puro (fallback)
                    // headers já consumidos - continua como JSON lines usando reader existente
                }
            }

            // Loop JSON Lines (TCP puro ou fallback)
            String line;
            while (alive && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                handleLine(line);
            }
        } catch (IOException e) {
            if (alive) MinecraftUnityBridgeMod.LOGGER.info("[Ponte] Cliente {} desconectou: {}", getRemoteAddress(), e.getMessage());
        } finally {
            close();
        }
    }

    private void handleLine(String line) {
        try {
            JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
            server.onClientMessage(this, obj);
        } catch (Exception e) {
            MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Mensagem inválida de {}: {}", getRemoteAddress(), line);
            // tenta responder erro mas não fecha
        }
    }

    // === Write loop ===

    private void writeLoop() {
        try {
            while (alive) {
                String line = sendQueue.take();
                if (isWebSocketMode && wsHandshakeDone) {
                    sendWsText(line);
                } else {
                    rawOut.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    rawOut.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (alive) MinecraftUnityBridgeMod.LOGGER.warn("[Ponte] Erro de escrita para {}", getRemoteAddress());
        }
    }

    // === WebSocket helpers (mínimo RFC6455) ===

    private void doWsHandshake(String clientKey) throws IOException {
        String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest((clientKey + magic).getBytes(StandardCharsets.UTF_8));
            String accept = Base64.getEncoder().encodeToString(hash);
            String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "\r\n";
            rawOut.write(resp.getBytes(StandardCharsets.UTF_8));
            rawOut.flush();
            MinecraftUnityBridgeMod.LOGGER.info("[Ponte] WS handshake OK com {}", getRemoteAddress());
            // envia hello após handshake
            JsonObject hello = new JsonObject();
            hello.addProperty("type", "hello");
            hello.addProperty("version", "1.0");
            hello.addProperty("transport", "websocket");
            sendQueue.offer(hello.toString());
        } catch (Exception e) {
            throw new IOException("WS handshake failed", e);
        }
    }

    private void readWsFrames() throws IOException {
        DataInputStream dis = new DataInputStream(rawIn);
        while (alive) {
            int b1 = dis.readUnsignedByte();
            int b2 = dis.readUnsignedByte();
            boolean fin = (b1 & 0x80) != 0;
            int opcode = b1 & 0x0F;
            boolean masked = (b2 & 0x80) != 0;
            int payloadLen = b2 & 0x7F;

            if (opcode == 8) { // close
                break;
            }
            if (payloadLen == 126) payloadLen = dis.readUnsignedShort();
            else if (payloadLen == 127) payloadLen = (int) dis.readLong();

            byte[] mask = new byte[4];
            if (masked) dis.readFully(mask);
            byte[] payload = new byte[payloadLen];
            if (payloadLen > 0) dis.readFully(payload);

            if (masked) {
                for (int i = 0; i < payloadLen; i++) payload[i] ^= mask[i % 4];
            }

            if (opcode == 1) { // text
                String text = new String(payload, StandardCharsets.UTF_8);
                handleLine(text);
            } else if (opcode == 9) { // ping -> pong
                sendWsFrame(payload, 10);
            }
            // ignora outros opcodes
            if (!fin) {
                // fragmented não suportado mas toleramos
            }
        }
    }

    private void sendWsText(String text) throws IOException {
        sendWsFrame(text.getBytes(StandardCharsets.UTF_8), 1);
    }

    private synchronized void sendWsFrame(byte[] payload, int opcode) throws IOException {
        // server -> client não mascara
        rawOut.write(0x80 | opcode);
        if (payload.length < 126) {
            rawOut.write(payload.length);
        } else if (payload.length < 65536) {
            rawOut.write(126);
            rawOut.write((payload.length >> 8) & 0xFF);
            rawOut.write(payload.length & 0xFF);
        } else {
            rawOut.write(127);
            // 8 bytes big-endian
            for (int i = 7; i >= 0; i--) rawOut.write((payload.length >> (i * 8)) & 0xFF);
        }
        rawOut.write(payload);
        rawOut.flush();
    }
}
