#if UNITY_EDITOR
using UnityEditor;
using UnityEngine;
using Ponte.MinecraftUnity;

namespace Ponte.MinecraftUnity.Editor
{
    [CustomEditor(typeof(MinecraftBridgeClient))]
    public class BridgeManagerEditor : UnityEditor.Editor
    {
        public override void OnInspectorGUI()
        {
            base.OnInspectorGUI();
            var c = (MinecraftBridgeClient)target;
            EditorGUILayout.Space();
            EditorGUILayout.HelpBox($"Status: {c.statusText}\nBlocos: {c.blocksReceived} | MPS: {c.messagesPerSecond}", MessageType.Info);
            GUILayout.BeginHorizontal();
            if (GUILayout.Button("Conectar")) c.Connect();
            if (GUILayout.Button("Desconectar")) c.Disconnect();
            GUILayout.EndHorizontal();
            if (!Application.isPlaying) EditorGUILayout.HelpBox("Entre em Play para conectar.", MessageType.None);
        }
    }

    public static class PonteMenu
    {
        [MenuItem("GameObject/Ponte/Minecraft Bridge Manager", false, 10)]
        static void CreateBridgeManager()
        {
            var go = new GameObject("BridgeManager");
            go.AddComponent<MinecraftBridgeClient>();
            go.AddComponent<WorldBuilder>();
            go.AddComponent<BridgeUI>();
            Undo.RegisterCreatedObjectUndo(go, "Create BridgeManager");

            // Setup mapper asset se não existir
            var mapper = AssetDatabase.LoadAssetAtPath<BlockTypeMapper>("Assets/MinecraftBridge/BlockTypeMapper.asset");
            if (mapper == null)
            {
                var m = ScriptableObject.CreateInstance<BlockTypeMapper>();
                m.AddDefaultEntries();
                System.IO.Directory.CreateDirectory("Assets/MinecraftBridge");
                AssetDatabase.CreateAsset(m, "Assets/MinecraftBridge/BlockTypeMapper.asset");
                AssetDatabase.SaveAssets();
                go.GetComponent<WorldBuilder>().mapper = m;
            }
            else go.GetComponent<WorldBuilder>().mapper = mapper;

            // Camera setup
            if (Camera.main != null && Camera.main.GetComponent<FlyCamera>() == null)
                Camera.main.gameObject.AddComponent<FlyCamera>();

            Selection.activeGameObject = go;
            Debug.Log("[Ponte] BridgeManager criado! Configure host/porta e dê Play.");
        }

        [MenuItem("Ponte/Documentação")]
        static void Docs() => Application.OpenURL("https://github.com/NEYMAROFICIALCONFIA123/MINECRAFT/blob/main/README.md");
    }
}
#endif
