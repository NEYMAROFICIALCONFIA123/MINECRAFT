/**
 * Relay WebSocket → TCP para a Ponte Minecraft-Unity
 * 
 * Uso: node server.js
 * - Escuta WS em 0.0.0.0:25565 (para Unity WebGL / browsers)
 * - Repassa para TCP 127.0.0.1:25566 (onde o mod Fabric escuta)
 * 
 * Unity WebGL não pode abrir TcpClient, então conecta aqui via WebSocket.
 */

const net = require('net');
const WebSocket = require('ws');

const WS_PORT = process.env.WS_PORT ? parseInt(process.env.WS_PORT, 10) : 25565;
const MC_HOST = process.env.MC_HOST || '127.0.0.1';
const MC_PORT = process.env.MC_PORT ? parseInt(process.env.MC_PORT, 10) : 25566;

const wss = new WebSocket.Server({ port: WS_PORT });

console.log(`🌉 Relay Ponte iniciado`);
console.log(`   WS  escutando em 0.0.0.0:${WS_PORT}`);
console.log(`   TCP alvo ${MC_HOST}:${MC_PORT}`);
console.log(`   Unity WebGL deve conectar em ws://SEU_IP:${WS_PORT}`);

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress;
  console.log(`[Relay] Unity WS conectado: ${ip}`);

  const mc = net.createConnection({ host: MC_HOST, port: MC_PORT }, () => {
    console.log(`[Relay] Conectado ao mod MC para ${ip}`);
  });

  let mcBuffer = '';

  mc.on('data', (chunk) => {
    // MC manda JSON Lines; repassa como WS frames
    mcBuffer += chunk.toString('utf8');
    let idx;
    while ((idx = mcBuffer.indexOf('\n')) !== -1) {
      const line = mcBuffer.slice(0, idx).trim();
      mcBuffer = mcBuffer.slice(idx + 1);
      if (line && ws.readyState === WebSocket.OPEN) {
        ws.send(line);
      }
    }
  });

  ws.on('message', (data) => {
    // Unity manda JSON; repassa como linha
    const str = data.toString();
    mc.write(str.endsWith('\n') ? str : str + '\n');
  });

  const cleanup = (who) => {
    console.log(`[Relay] Desconectado (${who}): ${ip}`);
    try { ws.close(); } catch {}
    try { mc.destroy(); } catch {}
  };

  ws.on('close', () => cleanup('WS close'));
  ws.on('error', (e) => { console.warn(`[Relay] WS erro ${ip}: ${e.message}`); cleanup('WS error'); });
  mc.on('close', () => cleanup('MC close'));
  mc.on('error', (e) => { console.warn(`[Relay] MC erro ${ip}: ${e.message}`); ws.close(1011, e.message); });
});

wss.on('listening', () => console.log(`[Relay] Pronto!`));
wss.on('error', (e) => console.error(`[Relay] WS erro:`, e.message));
