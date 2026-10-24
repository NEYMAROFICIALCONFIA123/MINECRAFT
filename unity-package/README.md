# Unity Package — Ponte

Veja `../README.md` para guia geral.

## Instalação como package

`Package Manager > + > Add from disk > unity-package/package.json`

## Scripts

- `Core/MinecraftBridgeClient.cs` — cliente TCP
- `Core/BlockTypeMapper.cs` — cores/materiais
- `Core/BridgeProtocol.cs` — mensagens
- `World/WorldBuilder.cs` — instancia blocos
- `World/FlyCamera.cs` — câmera livre
- `UI/BridgeUI.cs` — controles (pintar/deletar/fill/tempo/clima)
- `UI/ChatPanel.cs` — chat
- `Editor/BridgeManagerEditor.cs` — inspector + menu

## Demos

`Samples~/Demo` — cena de exemplo.
