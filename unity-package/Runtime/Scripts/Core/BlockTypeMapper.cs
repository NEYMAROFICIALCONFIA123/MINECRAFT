using System.Collections.Generic;
using UnityEngine;

namespace Ponte.MinecraftUnity
{
    /// <summary>
    /// Mapeia block IDs do Minecraft para materiais/meshes na Unity.
    /// Você pode estender adicionando prefabs custom.
    /// </summary>
    [CreateAssetMenu(menuName = "Ponte/BlockTypeMapper", fileName = "BlockTypeMapper")]
    public class BlockTypeMapper : ScriptableObject
    {
        [System.Serializable]
        public class Entry
        {
            public string blockId; // ex: minecraft:stone
            public Color color = Color.white;
            public float metallic;
            public float smoothness;
            [Tooltip("Se true, bloco é transparente (vidro, água, etc)")]
            public bool transparent;
            [Tooltip("Prefab opcional para este bloco (ex: escada)")]
            public GameObject prefab;
        }

        public List<Entry> entries = new();

        // Paleta padrão das 16 cores vanilla para preview
        public static Dictionary<string, Color> VanillaPalette = new()
        {
            {"white", new Color(0.91f,0.92f,0.92f)},
            {"light_gray", new Color(0.70f,0.70f,0.70f)},
            {"gray", new Color(0.39f,0.39f,0.39f)},
            {"black", new Color(0.10f,0.10f,0.10f)},
            {"brown", new Color(0.45f,0.28f,0.16f)},
            {"red", new Color(0.63f,0.15f,0.13f)},
            {"orange", new Color(0.89f,0.47f,0.13f)},
            {"yellow", new Color(0.99f,0.77f,0.15f)},
            {"lime", new Color(0.44f,0.73f,0.10f)},
            {"green", new Color(0.23f,0.36f,0.07f)},
            {"cyan", new Color(0.08f,0.47f,0.53f)},
            {"light_blue", new Color(0.15f,0.54f,0.78f)},
            {"blue", new Color(0.17f,0.18f,0.56f)},
            {"purple", new Color(0.39f,0.13f,0.61f)},
            {"magenta", new Color(0.66f,0.19f,0.62f)},
            {"pink", new Color(0.84f,0.40f,0.56f)},
        };

        private Dictionary<string, Entry> lookup;

        void OnEnable() => RebuildLookup();

        public void RebuildLookup()
        {
            lookup = new Dictionary<string, Entry>();
            foreach (var e in entries) if (!string.IsNullOrEmpty(e.blockId)) lookup[e.blockId] = e;
        }

        public Entry Get(string blockId)
        {
            if (lookup == null) RebuildLookup();
            if (lookup.TryGetValue(blockId, out var e)) return e;
            // fallback: tenta sem namespace
            string shortId = blockId.Contains(":") ? blockId.Split(':')[1] : blockId;
            foreach (var kv in lookup) if (kv.Key.EndsWith(shortId)) return kv.Value;
            return null;
        }

        public Color ColorFor(string blockId)
        {
            var e = Get(blockId);
            if (e != null) return e.color;

            // Heurística por nome
            if (blockId.Contains("grass")) return new Color(0.35f, 0.6f, 0.2f);
            if (blockId.Contains("dirt")) return new Color(0.55f, 0.35f, 0.2f);
            if (blockId.Contains("stone")) return new Color(0.6f, 0.6f, 0.6f);
            if (blockId.Contains("sand")) return new Color(0.85f, 0.8f, 0.5f);
            if (blockId.Contains("wood") || blockId.Contains("log") || blockId.Contains("plank")) return new Color(0.65f, 0.45f, 0.2f);
            if (blockId.Contains("leaf")) return new Color(0.2f, 0.5f, 0.15f);
            if (blockId.Contains("water")) return new Color(0.2f, 0.4f, 0.9f) { a = 0.6f };
            if (blockId.Contains("lava")) return new Color(0.9f, 0.3f, 0.05f);
            if (blockId.Contains("glass")) return new Color(0.9f, 0.95f, 1f) { a = 0.35f };
            if (blockId.Contains("wool") || blockId.Contains("concrete") || blockId.Contains("terracotta"))
            {
                foreach (var kv in VanillaPalette) if (blockId.Contains(kv.Key)) return kv.Value;
            }
            return Color.gray;
        }

        public bool IsTransparent(string blockId)
        {
            var e = Get(blockId);
            if (e != null) return e.transparent;
            return blockId.Contains("glass") || blockId.Contains("water") || blockId.Contains("air");
        }

        // Instancia um Entry padrão para edição no Inspector
        public void AddDefaultEntries()
        {
            string[] commons = {
                "minecraft:stone", "minecraft:dirt", "minecraft:grass_block", "minecraft:oak_planks",
                "minecraft:oak_log", "minecraft:glass", "minecraft:sand", "minecraft:gravel",
                "minecraft:water", "minecraft:lava", "minecraft:bedrock", "minecraft:bricks",
                "minecraft:white_concrete", "minecraft:red_concrete", "minecraft:blue_concrete",
                "minecraft:yellow_concrete", "minecraft:green_concrete", "minecraft:black_concrete"
            };
            entries.Clear();
            foreach (var id in commons)
            {
                entries.Add(new Entry { blockId = id, color = ColorFor(id) });
            }
        }
    }
}
