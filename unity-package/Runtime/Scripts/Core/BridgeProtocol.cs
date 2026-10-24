using System;
using System.Collections.Generic;
using UnityEngine;

namespace Ponte.MinecraftUnity
{
    // === Mensagens Minecraft -> Unity ===
    [Serializable]
    public class HelloMessage { public string type; public string version; public string world; public string motd; }

    [Serializable]
    public class BlockUpdateMessage
    {
        public string type; // blockUpdate
        public int x; public int y; public int z;
        public string block;
    }

    [Serializable]
    public class BlockBulkMessage
    {
        public string type; // blockBulk
        public BlockEntry[] blocks;
    }

    [Serializable]
    public class BlockEntry
    {
        public int x; public int y; public int z;
        public string block;
    }

    [Serializable]
    public class ChunkDataMessage
    {
        public string type; // chunkData
        public int chunkX; public int chunkZ;
        public BlockEntry[] blocks;
        public int count;
    }

    [Serializable]
    public class TimeUpdateMessage { public string type; public long time; public bool isDay; }

    [Serializable]
    public class WeatherUpdateMessage { public string type; public string weather; }

    [Serializable]
    public class ChatMessage { public string type; public string message; public string from; }

    [Serializable]
    public class WorldInfoMessage
    {
        public string type;
        public string dimension;
        public SpawnPos spawn;
        public long time;
        public bool raining;
        public bool thundering;
        [Serializable] public class SpawnPos { public int x; public int y; public int z; }
    }

    // === Mensagens Unity -> Minecraft ===

    [Serializable]
    public class SetBlockRequest
    {
        public string type = "setBlock";
        public int x, y, z;
        public string block;
        public SetBlockRequest(int x, int y, int z, string block) { this.x = x; this.y = y; this.z = z; this.block = block; }
    }

    [Serializable]
    public class FillRequest
    {
        public string type = "fill";
        public Pos from; public Pos to;
        public string block;
        [Serializable] public class Pos { public int x, y, z; public Pos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; } }
        public FillRequest(Vector3Int a, Vector3Int b, string block)
        {
            from = new Pos(a.x, a.y, a.z);
            to = new Pos(b.x, b.y, b.z);
            this.block = block;
        }
    }

    [Serializable]
    public class SetBlockColorRequest
    {
        public string type = "setBlockColor";
        public int x, y, z;
        public string color; // #RRGGBB
        public string material = "concrete";
        public SetBlockColorRequest(int x, int y, int z, string hex, string mat = "concrete") { this.x = x; this.y = y; this.z = z; color = hex; material = mat; }
    }

    [Serializable]
    public class SetTimeRequest { public string type = "setTime"; public long time; public SetTimeRequest(long t) { time = t; } }

    [Serializable]
    public class SetWeatherRequest { public string type = "setWeather"; public string weather; public SetWeatherRequest(string w) { weather = w; } }

    [Serializable]
    public class TeleportRequest { public string type = "teleport"; public double x, y, z; public string player; public TeleportRequest(double x, double y, double z) { this.x = x; this.y = y; this.z = z; } }

    [Serializable]
    public class ChatSendRequest { public string type = "chat"; public string message; public string from = "Unity"; public ChatSendRequest(string m) { message = m; } }

    [Serializable]
    public class CommandRequest { public string type = "command"; public string command; public CommandRequest(string c) { command = c; } }
}
