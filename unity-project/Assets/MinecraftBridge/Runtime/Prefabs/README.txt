Prefabs da Ponte:

- BridgeManager.prefab: contém MinecraftBridgeClient + WorldBuilder + BridgeUI
  Arraste para a cena. Configure host/port.

Para criar automaticamente: GameObject > Ponte > Minecraft Bridge Manager

Materiais:
- Crie um material URP Lit e atribua em WorldBuilder.baseMaterial
- Ative "Base Map" e use vertex colors se desejar variação.

Blocos:
- Cada bloco é um cube com MaterialPropertyBlock (cor por bloco)
- Para meshing otimizado (milhares de blocos), substitua WorldBuilder por um sistema de greedy meshing.
