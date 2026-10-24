# Guia de Uso

## Fluxo Básico

1. Construa normalmente no Minecraft (coloque/quebre blocos)
2. Veja a réplica surgir na Unity em tempo real
3. Na Unity, pinte/deleite/controle tempo; veja o Minecraft refletir instantaneamente

---

## Controles na Unity

### Câmera

| Ação | Tecla |
|------|-------|
| Mover | WASD |
| Subir/Descer | Q/E ou Espaço/Ctrl |
| Correr | Shift |
| Olhar | Botão direito + mouse |
| Zoom | Scroll |

### Ferramentas (clique no mundo)

- **🎨 Pintar:** escolha cor na paleta + material (concrete/wool/terracotta) → clique no bloco. Ele muda nos dois lados.
- **🗑️ Deletar:** clique no bloco → vira ar no MC e na Unity.
- **🧱 Fill:** menu Fill → clique no 1º ponto → clique no 2º ponto → preenche volume com bloco selecionado.
- **Ctrl+Click:** teleporta o player do Minecraft para o bloco clicado.

### Painel (OnGUI ou Canvas)

Se você não configurou Canvas, o `BridgeUI` desenha um painel automático no canto superior esquerdo:

- Seletores de ferramenta
- Paleta RGB + swatch
- Escolha de material
- Bloco para Fill (stone, glass, etc)
- Botões de tempo (amanhecer/meio-dia/pôr-do-sol/meia-noite)
- Botões de clima (limpo/chuva/tempestade)
- Status da conexão

### Chat

- Digite no `ChatPanel` (se configurado com InputField) → aparece no chat do Minecraft como `[Unity] sua mensagem`
- Mensagens do Minecraft aparecem no painel da Unity

---

## Exemplos Práticos

### Criar uma casa e pintar

1. No Minecraft, erga paredes com `stone`
2. Na Unity, selecione rosa + concrete → clique nas paredes → elas viram `pink_concrete` no MC
3. Selecione `glass` → use Fill para criar janelas

### Noite estrelada para print

1. Na Unity, clique 🌙 Meia-noite (ou arraste slider para 18000)
2. Clique ☀️ Limpo para tirar chuva
3. Faça o print no Minecraft

### Construir ponte gigante via Fill

1. Na Unity, set `selectedBlockId = minecraft:bricks`
2. Fill do ponto A (0,64,0) ao B (50,64,10) → ponte instantânea no MC

### Exportar construção

```csharp
var json = world.ExportSelection(new Vector3Int(0,64,0), new Vector3Int(20,70,20));
System.IO.File.WriteAllText("minha_casa.json", json);
```

---

## Dicas de Performance

- `WorldBuilder.maxBlocks` padrão 200k — aumente se sua build for enorme, mas atente para FPS
- Use `hideUnderground` para não renderizar blocos abaixo de Y=40 se só interessa superfície
- Para cidades gigantes, troque `WorldBuilder` por meshing (greedy) — exemplo futuro em `docs/MESHING.md`

---

## Multi-cliente

Vários Unitys podem conectar simultaneamente (até `maxClients`). Todos recebem os mesmos `blockUpdate` e veem as mesmas edições.

## Segurança

- Em servidores públicos, ative `requireOpForUnityActions` na config para exigir OP
- Desative `allowCommands` se não quiser que Unity execute `/commands`
