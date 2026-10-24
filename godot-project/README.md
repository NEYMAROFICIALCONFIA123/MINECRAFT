# 🎮 Ponte Minecraft → Godot 4

Este é o lado Godot da ponte. Mesmo protocolo do Unity, mas em GDScript.

## Requisitos

- **Godot 4.2+** (Forward Plus ou Compatibility)
- Mesmo **mod Fabric** do Minecraft (não precisa mudar nada no mod! Ele já fala TCP 25566)

## Como abrir

1. Baixe `godot-project` (pasta `godot-project/` do repo ou o zip `MinecraftPonte-Godot.zip`)
2. Abra o **Godot** > `Importar` > selecione a pasta `godot-project` > `Abrir`
3. Abra a cena `scenes/Main.tscn` e clique **▶️ Play**

> Se der erro de `BridgeClient` Autoload, vá em `Projeto > Configurações do Projeto > Autoload` e adicione `scripts/Core/MinecraftBridgeClient.gd` como `BridgeClient`.

## Como usar

Mesmo uso da versão Unity, mas com controles Godot:

| Ação | Tecla |
|------|-------|
| Mover | WASD |
| Subir/Descer | E / Q ou Espaço / Ctrl |
| Correr | Shift |
| Olhar | Botão direito + mouse |
| Pintar/Deletar/Fill | Clique esquerdo no bloco |
| Teleportar | Ctrl + Clique |
| Zoom | Scroll |

### Painel (CanvasLayer no topo esquerdo)

- **🎨 Pintar:** escolhe cor no ColorPicker + material (concrete/wool/terracotta) → clique no bloco
- **🗑️ Deletar:** selecione → clique
- **🧱 Fill:** clique no 1º ponto, depois no 2º → preenche com `selected_block_id`
- **Tempo:** botões 🌅 Dia / ☀️ Meio / 🌇 Tarde / 🌙 Noite (enviam `setTime` pro MC)
- **Clima:** ☀️ Limpo / 🌧️ Chuva / ⛈️ Tempestade
- **Limpar Mundo Local:** só limpa na Godot, não no MC

### Chat

Adicione o nó `ChatPanel` na cena se quiser chat (já tem script pronto em `scripts/UI/ChatPanel.gd`).

## Estrutura

```
godot-project/
├── project.godot
├── scenes/Main.tscn          # Cena principal (BridgeClient + WorldBuilder + Camera + UI)
├── scripts/Core/
│   ├── MinecraftBridgeClient.gd  # TCP + JSON Lines + signals
│   └── BlockTypeMapper.gd        # cores vanilla 16
├── scripts/World/
│   ├── WorldBuilder.gd       # instancia BoxMesh por bloco, pooling
│   └── FlyCamera.gd          # câmera livre
└── scripts/UI/
    ├── BridgeUI.gd           # painel completo (pintar, fill, tempo, clima)
    └── ChatPanel.gd
```

## Teste sem Minecraft

Use o simulador Python (mesmo da Unity):
```bash
python tools/simulate_minecraft.py
# Godot conecta em 127.0.0.1:25566 igual
```

## Diferença Unity vs Godot

- **Mesmo mod**, mesmo protocolo, mesma porta — só muda o cliente.
- Unity usa `TcpClient` + `StreamReader`; Godot usa `StreamPeerTCP` + `poll()`.
- Godot é mais leve e open-source, ideal se tu não quer pagar Unity.

## Dicas de performance

- `WorldBuilder.max_blocks` padrão 200k — aumente se sua cidade for gigante
- Para mundos enormes, troque `WorldBuilder` por `MultiMeshInstance3D` (1 draw call)
