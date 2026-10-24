# Instalação Detalhada

## Requisitos

| Lado | Requisito | Versão |
|------|-----------|--------|
| Minecraft | Fabric Loader | 0.14.22+ |
| Minecraft | Fabric API | 0.92+ |
| Minecraft | Java | 17 |
| Minecraft | MC | 1.20.1 (compatível 1.20.x) |
| Unity | Unity | 2021.3 LTS+ (URP recomendado) |
| Relay opcional | Node.js | 18+ |

---

## 1. Mod Fabric

### Build local

```bash
cd minecraft-mod
# Se tiver Gradle instalado global, ou use wrapper:
./gradlew build
# Saída: build/libs/minecraft-unity-ponte-1.0.0.jar
```

> Sem Java local? Baixe o jar pré-compilado em Releases (quando publicado) e pule o build.

### Instalar

1. Instale [Fabric Loader](https://fabricmc.net/use/) para 1.20.1
2. Copie `fabric-api-0.92.0+1.20.1.jar` para `mods/`
3. Copie `minecraft-unity-ponte-1.0.0.jar` para `mods/`
4. Inicie o jogo, abra um mundo
5. Verifique o log (`logs/latest.log`):

```
[Ponte] Servidor TCP iniciado na porta 25566
[Ponte] Servidor WebSocket iniciado na porta 25565
```

### Config

`config/minecraft-unity-bridge.json` é criado no primeiro boot. Edite com mundo fechado:

```json
{
  "tcpPort": 25566,
  "webSocketPort": 25565,
  "syncRadiusChunks": 8,
  "maxClients": 8,
  "enableTimeControl": true,
  "enableWeatherControl": true,
  "enableTeleport": true,
  "allowCommands": true,
  "requireOpForUnityActions": false,
  "blockUpdateThrottleMs": 16,
  "verboseLogging": false
}
```

Reinicie o mundo após alterar.

### Firewall

Libere inbound TCP nas portas configuradas. No Windows: Firewall > Regras de entrada > Nova regra > TCP 25566-25565.

---

## 2. Unity

### Via Package Manager (recomendado)

1. Abra seu projeto Unity 2021.3+
2. `Window > Package Manager`
3. `+` > `Add package from disk` > selecione `unity-package/package.json`
4. Alternativamente: copie `unity-package/Runtime` para `Assets/MinecraftBridge/`

### Setup da cena

**Automático:**
`GameObject > Ponte > Minecraft Bridge Manager` (criado pelo editor script)

**Manual:**
1. Crie Empty `BridgeManager`
2. Add Component `MinecraftBridgeClient` → configure `host=127.0.0.1`, `port=25566`
3. Add Component `WorldBuilder` → arraste `BlockTypeMapper` e um `Material` URP Lit
4. Add Component `BridgeUI`
5. Na Main Camera, Add Component `FlyCamera`

### Material

- Crie `Materials/Block.mat` com shader `Universal Render Pipeline/Lit`
- Marque `Base Map` branco, deixe Unity gerenciar cor via MaterialPropertyBlock
- Para transparência (vidro/água), duplique material com `Surface Type = Transparent`

### Rodar

1. Primeiro inicie o mundo no Minecraft
2. No Unity, pressione Play ▶
3. Observe o status: `Conectado` + blocos surgindo
4. Se `Erro: Connection refused`, verifique IP/porta e se o mundo está aberto

---

## 3. Relay (opcional, para WebGL ou rede externa)

Unity WebGL não suporta `TcpClient`. Use o relay WS:

```bash
cd relay
npm install
npm start
# WS em 0.0.0.0:25565 -> TCP 127.0.0.1:25566
```

Na Unity WebGL, mude o cliente para conectar via WebSocket (use plugin `NativeWebSocket` ou similar; a ponte já fala WS nativamente no mod, o relay é só para cenários com NAT).

---

## Troubleshooting

| Sintoma | Causa | Solução |
|---------|-------|---------|
| Log sem `[Ponte]` | Mod não carregou | Confira `mods/` contém o jar e Fabric API; veja `logs/latest.log` por erros |
| Unity `Connection refused` | Mundo não aberto ou porta errada | Abra mundo singleplayer → Open to LAN se necessário; cheque `config` |
| Blocos não aparecem | Raio de sync | Aumente `syncRadiusChunks`; movimente player para carregar chunks |
| Pintar não funciona | Material inválido | Use `concrete`/`wool`/`terracotta`; o mod converte hex para bloco mais próximo |
| Fill bloqueado | Volume >10000 | Reduza seleção; faça em etapas |
| Lag | Muitos blocos | Diminua `maxBlocks` no WorldBuilder; aumente `blockUpdateThrottleMs` |

Ainda com problemas? Abra uma issue com `logs/latest.log` + `config/minecraft-unity-bridge.json`.
