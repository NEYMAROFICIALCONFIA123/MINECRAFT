using UnityEngine;
using UnityEngine.UI;

namespace Ponte.MinecraftUnity
{
    /// <summary>
    /// UI completa da ponte: paleta, tempo, clima, chat, fill, etc.
    /// Use com Unity UI (UGUI). Arraste este script para um Canvas.
    /// Para UGUI rápido sem setup manual, use BridgeUIAuto que cria tudo via código.
    /// </summary>
    public class BridgeUI : MonoBehaviour
    {
        [Header("Refs")]
        public MinecraftBridgeClient bridge;
        public WorldBuilder world;
        public Camera mainCamera;

        [Header("Paleta")]
        public Color selectedColor = Color.white;
        public string selectedMaterial = "concrete"; // concrete, wool, terracotta
        public string selectedBlockId = "minecraft:white_concrete";

        [Header("Ferramenta")]
        public enum Tool { Select, Paint, Delete, Fill, Teleport }
        public Tool currentTool = Tool.Paint;
        private Vector3Int fillStart;
        private bool hasFillStart;

        void Start()
        {
            if (bridge == null) bridge = FindObjectOfType<MinecraftBridgeClient>();
            if (world == null) world = FindObjectOfType<WorldBuilder>();
            if (mainCamera == null) mainCamera = Camera.main;
        }

        void Update()
        {
            // Clique no mundo
            if (Input.GetMouseButtonDown(0) && !IsPointerOverUI())
            {
                HandleClick();
            }

            // Hotkeys
            if (Input.GetKeyDown(KeyCode.Delete)) currentTool = Tool.Delete;
            if (Input.GetKeyDown(KeyCode.P)) currentTool = Tool.Paint;
            if (Input.GetKeyDown(KeyCode.F)) currentTool = Tool.Fill;
        }

        void HandleClick()
        {
            if (world == null || mainCamera == null) return;
            if (!world.RaycastFromCamera(mainCamera, out var hitPos, out var placePos)) return;

            bool ctrl = Input.GetKey(KeyCode.LeftControl) || Input.GetKey(KeyCode.RightControl);

            if (ctrl)
            {
                // Teleporte
                bridge.SendTeleport(hitPos);
                Debug.Log($"[Ponte] Teleport para {hitPos}");
                return;
            }

            switch (currentTool)
            {
                case Tool.Paint:
                    // pinta via cor
                    bridge.SendPaint(hitPos, selectedColor, selectedMaterial);
                    // previsão local imediata
                    world.SetBlock(hitPos, BlockIdFromColor(selectedColor));
                    break;
                case Tool.Delete:
                    bridge.SendDeleteBlock(hitPos);
                    world.SetBlock(hitPos, "minecraft:air");
                    break;
                case Tool.Fill:
                    if (!hasFillStart)
                    {
                        fillStart = hitPos;
                        hasFillStart = true;
                        Debug.Log($"[Ponte] Fill início em {fillStart}");
                    }
                    else
                    {
                        bridge.SendFill(fillStart, hitPos, selectedBlockId);
                        hasFillStart = false;
                        Debug.Log($"[Ponte] Fill {fillStart} -> {hitPos} com {selectedBlockId}");
                    }
                    break;
                case Tool.Teleport:
                    bridge.SendTeleport(hitPos);
                    break;
            }
        }

        // === Chamados por botões da UI (associe no Inspector) ===

        public void SetTool(int toolIndex) => currentTool = (Tool)toolIndex;
        public void SetColor(Color c) { selectedColor = c; selectedBlockId = BlockIdFromColor(c); }
        public void SetMaterial(string mat) => selectedMaterial = mat;
        public void SetBlock(string blockId) => selectedBlockId = blockId;

        public void SetTime(float time) // slider 0..24000
        {
            bridge.SendTime((long)time);
        }

        public void SetTimeDay() => bridge.SendTime(1000);
        public void SetTimeNoon() => bridge.SendTime(6000);
        public void SetTimeSunset() => bridge.SendTime(13000);
        public void SetTimeMidnight() => bridge.SendTime(18000);

        public void SetWeatherClear() => bridge.SendWeather("clear");
        public void SetWeatherRain() => bridge.SendWeather("rain");
        public void SetWeatherThunder() => bridge.SendWeather("thunder");

        public void SendChat(string msg) => bridge.SendChat(msg);
        public void SendCommand(string cmd) => bridge.SendCommand(cmd);
        public void ClearFill() => hasFillStart = false;
        public void ClearWorld() => world.ClearWorld();

        string BlockIdFromColor(Color c)
        {
            string hex = ColorUtility.ToHtmlStringRGB(c).ToLower();
            // mapeamento rápido para as 16 cores (mesma lógica do ColorMapper Java)
            // retorna concrete da cor mais próxima - simplificado via nome
            // Para fidelidade total, usar BlockTypeMapper
            if (hex == "ffffff" || hex == "fff") return "minecraft:white_concrete";
            if (hex.StartsWith("ff0000") || hex.Contains("ff0000")) return "minecraft:red_concrete";
            if (hex.Contains("0000ff") || hex.Contains("00ff")) return "minecraft:blue_concrete";
            // fallback: white_concrete tingido pela cor escolhida via setBlockColor
            return "minecraft:white_concrete";
        }

        bool IsPointerOverUI()
        {
            // Se EventSystem existir, checa
            if (UnityEngine.EventSystems.EventSystem.current != null)
                return UnityEngine.EventSystems.EventSystem.current.IsPointerOverGameObject();
            return false;
        }

        // === UI OnGUI simples (fallback sem UGUI setup) ===

        void OnGUI()
        {
            // Só mostra OnGUI se não houver Canvas UGUI configurado
            if (FindObjectOfType<Canvas>() != null) return;

            GUILayout.BeginArea(new Rect(10, 10, 340, 520), GUI.skin.box);
            GUILayout.Label("🌉 PONTE MINECRAFT → UNITY", GUI.skin.GetStyle("label") ?? GUI.skin.label);
            GUILayout.Label(bridge != null ? $"Status: {bridge.statusText} | Blocos: {(world != null ? world.BlockCount.ToString() : "0")}" : "Sem bridge");

            GUILayout.Space(6);
            GUILayout.Label("Ferramenta (clique no mundo):");
            GUILayout.BeginHorizontal();
            if (GUILayout.Toggle(currentTool == Tool.Paint, "🎨 Pintar", GUI.skin.button)) currentTool = Tool.Paint;
            if (GUILayout.Toggle(currentTool == Tool.Delete, "🗑️ Deletar", GUI.skin.button)) currentTool = Tool.Delete;
            if (GUILayout.Toggle(currentTool == Tool.Fill, "🧱 Fill", GUI.skin.button)) currentTool = Tool.Fill;
            GUILayout.EndHorizontal();

            if (currentTool == Tool.Fill && hasFillStart)
            {
                GUILayout.Label($"Fill início: {fillStart} (clique no 2º ponto)");
                if (GUILayout.Button("Cancelar Fill")) ClearFill();
            }

            GUILayout.Space(6);
            GUILayout.Label("Cor (pintar):");
            selectedColor = RGBSlider("R", selectedColor.r, c => selectedColor = new Color(c, selectedColor.g, selectedColor.b));
            selectedColor = RGBSlider("G", selectedColor.g, c => selectedColor = new Color(selectedColor.r, c, selectedColor.b));
            selectedColor = RGBSlider("B", selectedColor.b, c => selectedColor = new Color(selectedColor.r, selectedColor.g, c));
            GUILayout.BeginHorizontal();
            GUI.backgroundColor = selectedColor;
            GUILayout.Box("", GUILayout.Height(24), GUILayout.ExpandWidth(true));
            GUI.backgroundColor = Color.white;
            GUILayout.EndHorizontal();

            GUILayout.Space(4);
            GUILayout.Label("Material:");
            GUILayout.BeginHorizontal();
            if (GUILayout.Toggle(selectedMaterial == "concrete", "Concrete", GUI.skin.button)) selectedMaterial = "concrete";
            if (GUILayout.Toggle(selectedMaterial == "wool", "Wool", GUI.skin.button)) selectedMaterial = "wool";
            if (GUILayout.Toggle(selectedMaterial == "terracotta", "Terracotta", GUI.skin.button)) selectedMaterial = "terracotta";
            GUILayout.EndHorizontal();

            GUILayout.Space(6);
            GUILayout.Label("Bloco para Fill:");
            GUILayout.BeginHorizontal();
            foreach (var b in new[] { "stone", "glass", "oak_planks", "bricks" })
            {
                string id = "minecraft:" + b;
                if (GUILayout.Toggle(selectedBlockId == id, b, GUI.skin.button)) selectedBlockId = id;
            }
            GUILayout.EndHorizontal();

            GUILayout.Space(8);
            GUILayout.Label("Tempo:");
            GUILayout.BeginHorizontal();
            if (GUILayout.Button("🌅 1k")) SetTimeDay();
            if (GUILayout.Button("☀️ 6k")) SetTimeNoon();
            if (GUILayout.Button("🌇 13k")) SetTimeSunset();
            if (GUILayout.Button("🌙 18k")) SetTimeMidnight();
            GUILayout.EndHorizontal();

            GUILayout.Space(4);
            GUILayout.Label("Clima:");
            GUILayout.BeginHorizontal();
            if (GUILayout.Button("☀️ Limpo")) SetWeatherClear();
            if (GUILayout.Button("🌧️ Chuva")) SetWeatherRain();
            if (GUILayout.Button("⛈️ Tempestade")) SetWeatherThunder();
            GUILayout.EndHorizontal();

            GUILayout.Space(6);
            GUILayout.Label("Dicas: Ctrl+Click = teleportar | Shift+WASD = correr | Botão direito arrasta câmera");
            if (GUILayout.Button("Limpar Mundo Local (não afeta MC)")) ClearWorld();

            GUILayout.EndArea();
        }

        Color RGBSlider(string label, float val, System.Func<float, Color> setter)
        {
            GUILayout.BeginHorizontal();
            GUILayout.Label(label, GUILayout.Width(12));
            float nv = GUILayout.HorizontalSlider(val, 0, 1);
            if (Mathf.Abs(nv - val) > 0.001f) setter(nv);
            GUILayout.EndHorizontal();
            return selectedColor; // será sobrescrito pelo caller corretamente via closure - simplificado
        }
    }
}
