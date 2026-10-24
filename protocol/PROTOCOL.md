# Protocolo da Ponte — v1.0

Transporte: **JSON Lines sobre TCP** (porta padrão `25566`) e **WebSocket text frames** (`25565`). Uma linha = um objeto JSON + `\n`. Em WS, cada `send` é um frame `opcode 1`.

## Fluxo

1. Unity conecta via `TcpClient` em `host:port`.
2. Minecraft envia `hello` imediatamente.
3. Minecraft envia `worldInfo` + `N x chunkData` (sync inicial) + `syncComplete`.
4. A partir daí, fluxo bidirecional:
   - Minecraft → Unity: `blockUpdate`/`blockBulk`/`timeUpdate`/`chat`/...
   - Unity → Minecraft: `setBlock`/`setBlockColor`/`fill`/`setTime`/...

## Mensagens Minecraft → Unity

### hello
```json
{"type":"hello","version":"1.0","world":"minecraft:overworld","motd":"Ponte ativa"}
```

### worldInfo
```json
{"type":"worldInfo","dimension":"minecraft:overworld","spawn":{"x":0,"y":64,"z":0},"time":6000,"raining":false,"thundering":false}
```

### blockUpdate
```json
{"type":"blockUpdate","x":10,"y":64,"z":-5,"block":"minecraft:stone"}
```
`block` é sempre `minecraft:air` para remoção.

### blockBulk
```json
{"type":"blockBulk","blocks":[{"x":10,"y":64,"z":-5,"block":"minecraft:stone"}, {"x":11,"y":64,"z":-5,"block":"minecraft:dirt"}]}
```
Enviado quando múltiplas mudanças coalescem dentro de `blockUpdateThrottleMs` (padrão 16ms).

### chunkData
```json
{"type":"chunkData","chunkX":0,"chunkZ":0,"blocks":[{"x":0,"y":64,"z":0,"block":"minecraft:grass_block"}], "count":1}
```
Batches de até ~2500 blocos por mensagem. Chunks vazios não são enviados.

### syncComplete
```json
{"type":"syncComplete","chunksSent":42}
```

### timeUpdate / weatherUpdate / chat / playerJoin / playerLeave
```json
{"type":"timeUpdate","time":6000,"isDay":true}
{"type":"weatherUpdate","weather":"rain"}
{"type":"chat","from":"Steve","message":"olá"}
{"type":"playerJoin","player":"Steve","uuid":"...","x":0,"y":64,"z":0}
{"type":"playerLeave","player":"Steve"}
```

---

## Mensagens Unity → Minecraft

### setBlock
```json
{"type":"setBlock","x":0,"y":64,"z":0,"block":"minecraft:glass"}
```
Use `"minecraft:air"` para deletar.

### setBlockColor (pintar)
```json
{"type":"setBlockColor","x":0,"y":64,"z":0,"color":"#FF0000","material":"concrete"}
```
`material`: `concrete` | `wool` | `terracotta` | `concrete_powder` | `glazed_terracotta`
O mod escolhe o bloco vanilla da cor mais próxima (distância RGB euclidiana).

### fill
```json
{"type":"fill","from":{"x":0,"y":64,"z":0},"to":{"x":5,"y":70,"z":5},"block":"minecraft:stone"}
```
Volume máximo 10.000 blocos por request (proteção). O mod executa na main thread.

### setTime
```json
{"type":"setTime","time":6000}
```
`0` amanhecer · `6000` meio-dia · `12000` anoitecer · `13000` pôr-do-sol · `18000` meia-noite · até `24000`.

### setWeather
```json
{"type":"setWeather","weather":"clear"}
```
Valores: `clear` | `rain` | `thunder`.

### teleport
```json
{"type":"teleport","x":100.5,"y":70,"z":-20.3,"player":"Steve"}
```
`player` opcional (se omitido, teleporta o primeiro online).

### chat / command / requestChunk / ping
```json
{"type":"chat","message":"olá do Unity!","from":"UnityPlayer"}
{"type":"command","command":"time set day"}
{"type":"requestChunk","chunkX":0,"chunkZ":0}
{"type":"ping","id":123}
```

Resposta a `ping`:
```json
{"type":"pong","id":123}
```

---

## Erros

O mod responde com:
```json
{"type":"error","message":"Volume muito grande: 20000 (max 10000)"}
```
E sucesso opcional:
```json
{"type":"ok","action":"setBlock"}
```

---

## Notas de implementação

- **Coalescing:** mudanças dentro de 16ms são agrupadas em `blockBulk`.
- **Thread safety:** o mod enfileira ações da Unity e executa via `server.execute()` na tick thread.
- **Backpressure:** filas limitadas a 8192 itens; excedentes são descartados com warning.
- **WS handshake:** `GET / HTTP/1.1` + `Sec-WebSocket-Key` → `101 Switching Protocols`.
- **Extensão:** você pode adicionar novos `type` sem quebrar compatibilidade; clientes devem ignorar tipos desconhecidos.
