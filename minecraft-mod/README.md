# Minecraft Mod — Ponte

Subprojeto Fabric do monorepo. Veja `../README.md` para guia geral.

## Build

```
./gradlew build
```

## Estrutura

- `MinecraftUnityBridgeMod.java` — entrypoint
- `server/BridgeServer.java` — TCP + WS
- `server/BridgeClient.java` — cliente individual
- `server/actions/UnityActionHandler.java` — executa comandos da Unity
- `mixin/ServerWorldMixin.java` — captura setBlockState
- `config/BridgeConfig.java`

## Teste rápido sem Unity

```bash
# conecta via netcat e vê mensagens
nc 127.0.0.1 25566
# ou
python3 -c "import socket,json; s=socket.create_connection(('127.0.0.1',25566)); print(s.makefile().readline())"
```
