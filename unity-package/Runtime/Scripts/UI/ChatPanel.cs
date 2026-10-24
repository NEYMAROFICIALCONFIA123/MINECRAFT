using UnityEngine;
using UnityEngine.UI;

namespace Ponte.MinecraftUnity
{
    /// <summary>
    /// Painel de chat bidirecional. Conecte ao MinecraftBridgeClient.
    /// </summary>
    public class ChatPanel : MonoBehaviour
    {
        public MinecraftBridgeClient bridge;
        public Text logText; // UGUI Text ou TMP
        public InputField inputField;
        private string log = "";

        void Start()
        {
            if (bridge == null) bridge = FindObjectOfType<MinecraftBridgeClient>();
            if (bridge != null) bridge.OnChat += OnChat;
        }

        void OnChat(ChatMessage msg)
        {
            string line = $"<b>{msg.from ?? "MC"}:</b> {msg.message}\n";
            log += line;
            if (logText != null) logText.text = log;
            else Debug.Log($"[Chat] {msg.from}: {msg.message}");
        }

        public void SendFromInput()
        {
            if (inputField == null || string.IsNullOrWhiteSpace(inputField.text)) return;
            string txt = inputField.text;
            bridge.SendChat(txt);
            AppendLocal("Você", txt);
            inputField.text = "";
        }

        void AppendLocal(string who, string msg)
        {
            log += $"<i>{who}:</i> {msg}\n";
            if (logText != null) logText.text = log;
        }
    }
}
