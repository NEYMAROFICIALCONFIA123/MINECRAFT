using System.Collections.Generic;
using UnityEngine;

namespace Ponte.MinecraftUnity
{
    /// <summary>
    /// Reconstrói o mundo Minecraft na Unity com cubos instanciados.
    /// Usa pooling + batching simples. Para mundos gigantes, considere chunk meshing (ex: greedy meshing).
    /// </summary>
    public class WorldBuilder : MonoBehaviour
    {
        [Header("Referências")]
        public MinecraftBridgeClient bridge;
        public BlockTypeMapper mapper;
        public Material baseMaterial; // URP/Lit com vertex color support
        public Transform worldRoot;

        [Header("Performance")]
        public int maxBlocks = 200_000;
        public bool usePooling = true;
        public bool hideUnderground = false;

        // Estado
        private readonly Dictionary<Vector3Int, GameObject> spawned = new();
        private readonly Queue<GameObject> pool = new();
        private GameObject cubePrefab; // primitivo interno

        void Awake()
        {
            if (worldRoot == null)
            {
                var go = new GameObject("WorldRoot");
                worldRoot = go.transform;
                worldRoot.SetParent(transform);
            }
            // Cria prototype de cubo sem precisar de prefab externo
            cubePrefab = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cubePrefab.SetActive(false);
            // Remove collider do prototype se quiser lidar com raycast custom
        }

        void Start()
        {
            if (bridge == null) bridge = FindObjectOfType<MinecraftBridgeClient>();
            if (bridge == null) { Debug.LogError("[Ponte] WorldBuilder sem bridge!"); return; }

            bridge.OnBlockUpdate += HandleSingle;
            bridge.OnBlockBulk += HandleBulk;
            bridge.OnChunkData += HandleChunk;
            bridge.OnHello += _ => Debug.Log("[Ponte] Hello recebido - pronto para sync");
        }

        void OnDestroy()
        {
            if (bridge != null)
            {
                bridge.OnBlockUpdate -= HandleSingle;
                bridge.OnBlockBulk -= HandleBulk;
                bridge.OnChunkData -= HandleChunk;
            }
        }

        // === Handlers ===

        void HandleSingle(BlockEntry e) => SetBlock(new Vector3Int(e.x, e.y, e.z), e.block);

        void HandleBulk(BlockEntry[] arr)
        {
            foreach (var e in arr) SetBlock(new Vector3Int(e.x, e.y, e.z), e.block);
        }

        void HandleChunk(ChunkDataMessage msg)
        {
            if (msg.blocks == null) return;
            foreach (var b in msg.blocks) SetBlock(new Vector3Int(b.x, b.y, b.z), b.block);
            Debug.Log($"[Ponte] Chunk {msg.chunkX},{msg.chunkZ} -> {msg.blocks.Length} blocos");
        }

        // === Núcleo: cria/atualiza/remove ===

        public void SetBlock(Vector3Int pos, string blockId)
        {
            bool isAir = blockId == "minecraft:air" || blockId == "air" || string.IsNullOrEmpty(blockId);

            if (isAir)
            {
                if (spawned.TryGetValue(pos, out var go))
                {
                    spawned.Remove(pos);
                    ReturnToPool(go);
                }
                return;
            }

            if (spawned.TryGetValue(pos, out var existing))
            {
                // Só atualiza cor/material se mudou
                UpdateVisual(existing, blockId);
                return;
            }

            if (spawned.Count >= maxBlocks)
            {
                Debug.LogWarning($"[Ponte] Limite de blocos atingido ({maxBlocks}) - aumente maxBlocks");
                return;
            }

            var instance = GetFromPool();
            instance.transform.SetParent(worldRoot);
            instance.transform.position = pos; // 1 unidade = 1 bloco, origem alinhada
            instance.transform.localScale = Vector3.one;
            instance.SetActive(true);
            UpdateVisual(instance, blockId);
            spawned[pos] = instance;
        }

        void UpdateVisual(GameObject go, string blockId)
        {
            var rend = go.GetComponent<MeshRenderer>();
            if (rend == null) return;

            Color c = mapper != null ? mapper.ColorFor(blockId) : Color.gray;
            bool transparent = mapper != null && mapper.IsTransparent(blockId);

            // Clona material por cor para evitar leak: usa MaterialPropertyBlock se possível
            var block = new MaterialPropertyBlock();
            rend.GetPropertyBlock(block);
            // _BaseColor para URP Lit, _Color para Standard
            block.SetColor("_BaseColor", c);
            block.SetColor("_Color", c);
            block.SetColor("_BaseColorMap", c);
            rend.SetPropertyBlock(block);

            // Transparência simples
            if (transparent)
            {
                // ajusta alpha via material separado
                // Para simplificar, apenas reduz opacidade visual
                var mats = rend.sharedMaterials;
                // não trocamos shader em runtime para não quebrar batching
            }

            go.name = $"{blockId} @ {go.transform.position}";
        }

        GameObject GetFromPool()
        {
            if (usePooling && pool.Count > 0)
            {
                var go = pool.Dequeue();
                go.SetActive(true);
                return go;
            }
            var inst = Instantiate(cubePrefab);
            inst.SetActive(true);
            // Adiciona BoxCollider para raycast (já vem no primitive)
            return inst;
        }

        void ReturnToPool(GameObject go)
        {
            go.SetActive(false);
            if (usePooling) pool.Enqueue(go);
            else Destroy(go);
        }

        // === Utilidades ===

        public void ClearWorld()
        {
            foreach (var kv in spawned) ReturnToPool(kv.Value);
            spawned.Clear();
        }

        public bool HasBlock(Vector3Int pos) => spawned.ContainsKey(pos);

        public int BlockCount => spawned.Count;

        // Raycast do mouse para selecionar bloco (usado pela UI)
        public bool RaycastFromCamera(Camera cam, out Vector3Int hitPos, out Vector3Int placePos)
        {
            hitPos = default; placePos = default;
            if (cam == null) return false;
            Ray ray = cam.ScreenPointToRay(Input.mousePosition);
            if (Physics.Raycast(ray, out var hit, 1000f))
            {
                Vector3 p = hit.point - hit.normal * 0.01f; // dentro do bloco clicado
                hitPos = Vector3Int.RoundToInt(p);
                // posição adjacente para colocar novo bloco
                Vector3 place = hit.point + hit.normal * 0.5f;
                placePos = Vector3Int.RoundToInt(place);
                return true;
            }
            return false;
        }

        // Exporta seleção como JSON (para salvar construção)
        public string ExportSelection(Vector3Int min, Vector3Int max)
        {
            var list = new List<object>();
            for (int x = min.x; x <= max.x; x++)
                for (int y = min.y; y <= max.y; y++)
                    for (int z = min.z; z <= max.z; z++)
                    {
                        var pos = new Vector3Int(x, y, z);
                        if (spawned.TryGetValue(pos, out var go))
                        {
                            // recupera blockId do nome (simplificado)
                            string blockId = go.name.Split(' ')[0];
                            list.Add(new { x, y, z, block = blockId });
                        }
                    }
            return JsonUtility.ToJson(new { blocks = list, count = list.Count });
        }
    }
}
