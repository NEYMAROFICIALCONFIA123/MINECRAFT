#!/usr/bin/env python3
"""
Simulador de Minecraft para testar Unity sem precisar do jogo.
Age como o mod: abre TCP 25566 e envia blocos fake.

Uso: python simulate_minecraft.py
 - Envia hello + worldInfo + chunkData fake
 - A cada tecla Enter, envia um blockUpdate aleatório
 - Recebe e loga comandos da Unity
"""
import socket, threading, json, time, random

HOST = "0.0.0.0"
PORT = 25566
BLOCKS = ["minecraft:stone","minecraft:grass_block","minecraft:oak_planks","minecraft:glass","minecraft:bricks","minecraft:sand","minecraft:white_concrete","minecraft:red_concrete","minecraft:blue_concrete"]

def handle(conn, addr):
    print(f"[SIM] Unity conectado: {addr}")
    def send(o):
        conn.sendall((json.dumps(o) + "\n").encode())
    # hello
    send({"type":"hello","version":"1.0","world":"minecraft:overworld","motd":"Simulador Ponte"})
    send({"type":"worldInfo","dimension":"minecraft:overworld","spawn":{"x":0,"y":64,"z":0},"time":6000,"raining":False,"thundering":False})
    # chunk fake 16x5x16
    blocks=[]
    for x in range(-8,8):
        for z in range(-8,8):
            blocks.append({"x":x,"y":64,"z":z,"block":"minecraft:grass_block"})
            if random.random()<0.2:
                blocks.append({"x":x,"y":65,"z":z,"block":random.choice(BLOCKS)})
    send({"type":"chunkData","chunkX":0,"chunkZ":0,"blocks":blocks,"count":len(blocks)})
    send({"type":"syncComplete","chunksSent":1})
    print(f"[SIM] Enviado chunk com {len(blocks)} blocos. Digite Enter para simular blockUpdate; 'time' para timeUpdate.")

    # thread de leitura
    def reader():
        f=conn.makefile()
        for line in f:
            line=line.strip()
            if not line: continue
            try:
                m=json.loads(line)
                print(f"[SIM] ← Unity: {m}")
                # auto-respostas simples
                if m.get("type")=="ping":
                    send({"type":"pong","id":m.get("id")})
                elif m.get("type")=="setBlock":
                    # ecoa de volta como blockUpdate para Unity ver refletido
                    send({"type":"blockUpdate","x":m["x"],"y":m["y"],"z":m["z"],"block":m["block"]})
                elif m.get("type")=="setBlockColor":
                    send({"type":"blockUpdate","x":m["x"],"y":m["y"],"z":m["z"],"block":"minecraft:"+m["color"].lstrip("#")+"_concrete"})
                elif m.get("type")=="setTime":
                    send({"type":"timeUpdate","time":m["time"],"isDay":m["time"]<12000})
                elif m.get("type")=="chat":
                    print(f"[CHAT SIM] {m.get('message')}")
                    send({"type":"chat","from":"SimMC","message":f"Eco: {m.get('message')}"})
            except Exception as e:
                print(f"[SIM] parse erro {e}: {line}")
    th=threading.Thread(target=reader,daemon=True); th.start()

    # loop de envio aleatório
    try:
        while True:
            cmd=input()
            if cmd=="": # Enter -> bloco aleatório
                send({"type":"blockUpdate","x":random.randint(-8,8),"y":65,"z":random.randint(-8,8),"block":random.choice(BLOCKS)})
                print("[SIM] blockUpdate aleatório enviado")
            elif cmd=="time":
                t=random.randint(0,24000)
                send({"type":"timeUpdate","time":t})
                print(f"[SIM] timeUpdate {t}")
            elif cmd.startswith("chat "):
                send({"type":"chat","from":"Steve","message":cmd[5:]})
    except:
        pass
    finally:
        conn.close()
        print("[SIM] desconectado")

s=socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind((HOST,PORT))
s.listen(8)
print(f"[SIM] Simulador ouvindo em {HOST}:{PORT} — conecte Unity agora!")
while True:
    c,a=s.accept()
    threading.Thread(target=handle,args=(c,a),daemon=True).start()
