# API para Desenvolvedores

## Minecraft Mod (Java)

### Enviar mensagem custom para Unity

```java
JsonObject msg = new JsonObject();
msg.addProperty("type", "meuEvento");
msg.addProperty("dado", 42);
MinecraftUnityBridgeMod.getInstance().getBridgeServer().broadcast(msg);
```

### Interceptar mensagens da Unity

Edite `UnityActionHandler.java`:

```java
case "meuComando":
    String arg = msg.get("arg").getAsString();
    // faça algo no mundo
    break;
```

### Modificar mapping de cor

`server/actions/ColorMapper.java` — ajuste `COLOR_RGB` com paleta custom.

---

## Unity (C#)

### Assinar eventos da ponte

```csharp
var bridge = FindObjectOfType<MinecraftBridgeClient>();
bridge.OnBlockUpdate += e => Debug.Log($"Bloco {e.block} em {e.x},{e.y},{e.z}");
bridge.OnChat += m => Debug.Log($"Chat: {m.message}");
bridge.OnTimeUpdate += t => Debug.Log($"Tempo: {t.time}");
```

### Enviar comandos

```csharp
bridge.SendSetBlock(new Vector3Int(0,64,0), "minecraft:diamond_block");
bridge.SendPaint(new Vector3Int(0,64,0), Color.red, "concrete");
bridge.SendFill(new Vector3Int(0,64,0), new Vector3Int(5,70,5), "minecraft:glass");
bridge.SendTime(6000); // meio-dia
bridge.SendWeather("clear");
bridge.SendTeleport(new Vector3(100,70,100));
bridge.SendChat("Olá do Unity!");
bridge.SendCommand("give @p diamond 64");
```

### Estender BlockTypeMapper

1. Crie `Create > Ponte > BlockTypeMapper`
2. Adicione Entries: `blockId` + `color` + `metallic` + `prefab`
3. Atribua em `WorldBuilder.mapper`

### Custom WorldBuilder (meshing avançado)

Substitua `WorldBuilder` por sua implementação que assina os mesmos eventos:

```csharp
bridge.OnChunkData += chunk => MyGreedyMesher.Build(chunk);
```

---

## Protocolo Raw

Se não usar Unity, pode conectar com Python/JS:

### Python exemplo

```python
import socket, json
s = socket.create_connection(("127.0.0.1", 25566))
for line in s.makefile():
    msg = json.loads(line)
    if msg["type"] == "blockUpdate":
        print(f"Bloco {msg['block']} em {msg['x']},{msg['y']},{msg['z']}")
    # enviar de volta:
    # s.sendall(b'{"type":"setTime","time":6000}\n')
```

### Node.js exemplo

```js
import net from 'net';
const s = net.createConnection(25566, '127.0.0.1');
s.on('data', d => d.toString().split('\n').forEach(l => {
  if(!l.trim()) return;
  const m = JSON.parse(l);
  console.log(m);
}));
s.write('{"type":"setWeather","weather":"clear"}\n');
```

---

## Adicionar nova funcionalidade bidirecional

1. Defina novo `type` no `protocol/protocol.json`
2. Implemente handler em `UnityActionHandler.java` (MC) e `MinecraftBridgeClient.cs` (Unity)
3. Documente em `protocol/PROTOCOL.md`
4. Atualize UIs (`BridgeUI.cs`)

Exemplo: adicionar `setBiome` ou `spawnMob` segue mesmo padrão.
