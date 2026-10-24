using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using UnityEngine;

namespace Ponte.MinecraftUnity
{
    /// <summary>
    /// Cliente TCP que conecta ao mod Fabric e dispara eventos.
    /// Singleton-like: adicione este MonoBehaviour ao BridgeManager.
    /// </summary>
    public class MinecraftBridgeClient : MonoBehaviour
    {
        [Header("Conexão")]
        public string host = "127.0.0.1";
        public int port = 25566;
        public bool autoConnect = true;
        public float reconnectDelay = 3f;

        [Header("Status (somente leitura)")]
        public bool isConnected;
        public string statusText = "Desconectado";
        public int blocksReceived;
        public int messagesPerSecond;

        // Eventos para WorldBuilder e UI se inscreverem
        public event Action<HelloMessage> OnHello;
        public event Action<BlockEntry> OnBlockUpdate;
        public event Action<BlockEntry[]> OnBlockBulk;
        public event Action<ChunkDataMessage> OnChunkData;
        public event Action<TimeUpdateMessage> OnTimeUpdate;
        public event Action<WeatherUpdateMessage> OnWeatherUpdate;
        public event Action<ChatMessage> OnChat;
        public event Action<WorldInfoMessage> OnWorldInfo;
        public event Action OnSyncComplete;

        private TcpClient tcp;
        private NetworkStream stream;
        private StreamReader reader;
        private StreamWriter writer;
        private Thread readThread;
        private readonly ConcurrentQueue<string> inbound = new();
        private readonly ConcurrentQueue<string> outbound = new();
        private CancellationTokenSource cts;

        // métricas
        private int msgCountThisSecond;
        private float mpsTimer;

        void Start()
        {
            if (autoConnect) Connect();
        }

        void Update()
        {
            // Processa mensagens recebidas na main thread
            while (inbound.TryDequeue(out var line))
            {
                blocksReceived++;
                msgCountThisSecond++;
                Dispatch(line);
            }

            // Envia pendentes
            if (writer != null && outbound.TryDequeue(out var outLine))
            {
                try { writer.WriteLine(outLine); writer.Flush(); } catch { }
            }

            // MPS
            mpsTimer += Time.deltaTime;
            if (mpsTimer >= 1f)
            {
                messagesPerSecond = msgCountThisSecond;
                msgCountThisSecond = 0;
                mpsTimer = 0;
            }

            // Reconexão automática
            if (!isConnected && autoConnect && cts == null)
            {
                // tenta reconectar após delay
            }
        }

        // === API pública para UI / WorldBuilder ===

        public void Connect()
        {
            if (isConnected) return;
            statusText = $"Conectando em {host}:{port}...";
            cts = new CancellationTokenSource();
            readThread = new Thread(() => ReadLoop(cts.Token)) { IsBackground = true, Name = "Ponte-Read" };
            readThread.Start();
            // Inicia thread de escrita é via Update (main thread) para simplicidade
            // Reconexão handle
            statusText = "Conectando...";
        }

        public void Disconnect()
        {
            cts?.Cancel();
            try { tcp?.Close(); } catch { }
            isConnected = false;
            statusText = "Desconectado";
            cts = null;
        }

        public void Send<T>(T payload)
        {
            string json = JsonUtility.ToJson(payload);
            outbound.Enqueue(json);
            if (isConnected && writer != null)
            {
                // flush imediato tentará no próximo Update mas também tenta aqui se em background? melhor só enqueue
            }
        }

        // Conveniências
        public void SendSetBlock(Vector3Int pos, string blockId) => Send(new SetBlockRequest(pos.x, pos.y, pos.z, blockId));
        public void SendDeleteBlock(Vector3Int pos) => Send(new SetBlockRequest(pos.x, pos.y, pos.z, "minecraft:air"));
        public void SendPaint(Vector3Int pos, Color color, string material = "concrete")
        {
            string hex = ColorUtility.ToHtmlStringRGB(color);
            Send(new SetBlockColorRequest(pos.x, pos.y, pos.z, "#" + hex, material));
        }
        public void SendFill(Vector3Int a, Vector3Int b, string blockId) => Send(new FillRequest(a, b, blockId));
        public void SendTime(long time) => Send(new SetTimeRequest(time));
        public void SendWeather(string w) => Send(new SetWeatherRequest(w));
        public void SendTeleport(Vector3 pos) => Send(new TeleportRequest(pos.x, pos.y + 1, pos.z));
        public void SendChat(string msg) => Send(new ChatSendRequest(msg));
        public void SendCommand(string cmd) => Send(new CommandRequest(cmd));

        void OnDestroy() => Disconnect();
        void OnApplicationQuit() => Disconnect();

        // === Threads ===

        private void ReadLoop(CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                try
                {
                    tcp = new TcpClient();
                    var ar = tcp.BeginConnect(host, port, null, null);
                    bool success = ar.AsyncWaitHandle.WaitOne(TimeSpan.FromSeconds(5));
                    if (!success) throw new SocketException();
                    tcp.EndConnect(ar);

                    stream = tcp.GetStream();
                    reader = new StreamReader(stream, Encoding.UTF8);
                    writer = new StreamWriter(stream, Encoding.UTF8) { AutoFlush = true };

                    isConnected = true;
                    statusText = $"Conectado em {host}:{port}";

                    // Flush de outbound pendente
                    while (outbound.TryDequeue(out var pending))
                    {
                        writer.WriteLine(pending);
                    }

                    string line;
                    while (!token.IsCancellationRequested && tcp.Connected && (line = reader.ReadLine()) != null)
                    {
                        inbound.Enqueue(line);
                    }
                }
                catch (Exception e)
                {
                    statusText = $"Erro: {e.Message} - reconectando em {reconnectDelay}s";
                    isConnected = false;
                }

                if (!token.IsCancellationRequested)
                {
                    isConnected = false;
                    Thread.Sleep((int)(reconnectDelay * 1000));
                    statusText = "Reconectando...";
                }
            }
        }

        private void Dispatch(string line)
        {
            if (string.IsNullOrWhiteSpace(line)) return;
            // Descobre tipo via parse simples
            string type = ExtractType(line);
            try
            {
                switch (type)
                {
                    case "hello":
                        OnHello?.Invoke(JsonUtility.FromJson<HelloMessage>(line));
                        statusText = "Sincronizado ✓";
                        break;
                    case "blockUpdate":
                        {
                            var m = JsonUtility.FromJson<BlockUpdateMessage>(line);
                            OnBlockUpdate?.Invoke(new BlockEntry { x = m.x, y = m.y, z = m.z, block = m.block });
                            break;
                        }
                    case "blockBulk":
                        {
                            // JsonUtility não lida com arrays top-level bem; parse manual simples
                            var m = JsonUtility.FromJson<BlockBulkMessage>(line);
                            if (m.blocks != null) OnBlockBulk?.Invoke(m.blocks);
                            else TryParseBulkManually(line);
                            break;
                        }
                    case "chunkData":
                        {
                            var m = JsonUtility.FromJson<ChunkDataMessage>(line);
                            OnChunkData?.Invoke(m);
                            break;
                        }
                    case "timeUpdate":
                        OnTimeUpdate?.Invoke(JsonUtility.FromJson<TimeUpdateMessage>(line));
                        break;
                    case "weatherUpdate":
                        OnWeatherUpdate?.Invoke(JsonUtility.FromJson<WeatherUpdateMessage>(line));
                        break;
                    case "chat":
                        OnChat?.Invoke(JsonUtility.FromJson<ChatMessage>(line));
                        break;
                    case "worldInfo":
                        OnWorldInfo?.Invoke(JsonUtility.FromJson<WorldInfoMessage>(line));
                        break;
                    case "syncComplete":
                        OnSyncComplete?.Invoke();
                        break;
                    case "ok":
                    case "error":
                    case "pong":
                        Debug.Log($"[Ponte] {line}");
                        break;
                    default:
                        // Algumas mensagens como blockBulk com array precisam fallback
                        if (line.Contains("\"blocks\"")) TryParseBulkManually(line);
                        else Debug.Log($"[Ponte] Mensagem desconhecida: {line}");
                        break;
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning($"[Ponte] Falha ao processar: {line} | {e.Message}");
            }
        }

        private void TryParseBulkManually(string line)
        {
            // Fallback simples: extrai blocos via mini parser
            // Usa Unity JsonUtility não suporta arrays genéricos, então fazemos parse manual leve
            // Para robustez, delegamos ao WorldBuilder um evento raw também se necessário
            Debug.LogWarning($"[Ponte] Bulk parse fallback necessário: {line.Substring(0, Mathf.Min(200, line.Length))}");
        }

        private string ExtractType(string json)
        {
            int idx = json.IndexOf("\"type\"");
            if (idx < 0) return "";
            int colon = json.IndexOf(':', idx);
            int q1 = json.IndexOf('"', colon + 1);
            int q2 = json.IndexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return "";
            return json.Substring(q1 + 1, q2 - q1 - 1);
        }
    }
}
