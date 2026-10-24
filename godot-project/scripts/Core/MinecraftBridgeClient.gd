extends Node
## Cliente TCP da Ponte Minecraft -> Godot
## Conecta no mod Fabric (TCP 25566, JSON Lines) e emite signals.
## Use como Autoload "BridgeClient" ou adicione manualmente na cena.

signal hello(data: Dictionary)
signal world_info(data: Dictionary)
signal block_update(pos: Vector3i, block_id: String)
signal block_bulk(blocks: Array)
signal chunk_data(chunk_x: int, chunk_z: int, blocks: Array)
signal time_update(time: int, is_day: bool)
signal weather_update(weather: String)
signal chat_message(data: Dictionary)
signal sync_complete(chunks_sent: int)
signal connection_status(connected: bool, text: String)
signal raw_message(data: Dictionary)

@export var host: String = "127.0.0.1"
@export var port: int = 25566
@export var auto_connect: bool = true
@export var reconnect_delay: float = 3.0
@export var verbose: bool = false

var _tcp := StreamPeerTCP.new()
var _connected: bool = false
var _buffer: String = ""
var _reconnect_timer: float = 0.0

# métricas
var blocks_received: int = 0
var messages_per_second: int = 0
var _msg_count_this_sec: int = 0
var _mps_timer: float = 0.0

func _ready():
	if auto_connect:
		connect_to_bridge()

func _process(delta):
	# MPS counter
	_mps_timer += delta
	if _mps_timer >= 1.0:
		messages_per_second = _msg_count_this_sec
		_msg_count_this_sec = 0
		_mps_timer = 0.0

	# reconexão
	if not _connected:
		_reconnect_timer -= delta
		if _reconnect_timer <= 0 and auto_connect:
			_try_connect()
		return

	_tcp.poll()
	var status = _tcp.get_status()
	if status != StreamPeerTCP.STATUS_CONNECTED:
		_on_disconnected("Conexão perdida: status %d" % status)
		return

	# lê dados disponíveis
	var available = _tcp.get_available_bytes()
	if available > 0:
		var result = _tcp.get_data(available)
		if result[0] == OK:
			var chunk: String = (result[1] as PackedByteArray).get_string_from_utf8()
			_buffer += chunk
			_process_buffer()
	elif status == StreamPeerTCP.STATUS_ERROR:
		_on_disconnected("Erro de socket")

func _process_buffer():
	# JSON Lines: cada \n é uma mensagem
	while "\n" in _buffer:
		var idx = _buffer.find("\n")
		var line = _buffer.substr(0, idx).strip_edges()
		_buffer = _buffer.substr(idx + 1)
		if line == "":
			continue
		_msg_count_this_sec += 1
		blocks_received += 1
		_dispatch(line)

func _dispatch(line: String):
	var json = JSON.new()
	var err = json.parse(line)
	if err != OK:
		if verbose: print("[Ponte] JSON inválido: ", line)
		return
	var data = json.get_data()
	if not data is Dictionary:
		return
	var type = data.get("type", "")
	raw_message.emit(data)
	match type:
		"hello":
			hello.emit(data)
			connection_status.emit(true, "Sincronizado ✓")
		"worldInfo":
			world_info.emit(data)
		"blockUpdate":
			var pos = Vector3i(int(data.get("x", 0)), int(data.get("y", 0)), int(data.get("z", 0)))
			var bid = str(data.get("block", "minecraft:air"))
			block_update.emit(pos, bid)
		"blockBulk":
			var arr = data.get("blocks", [])
			block_bulk.emit(arr)
			# também emite um por um pra quem prefere
			for b in arr:
				var p = Vector3i(int(b.get("x", 0)), int(b.get("y", 0)), int(b.get("z", 0)))
				block_update.emit(p, str(b.get("block", "")))
		"chunkData":
			chunk_data.emit(int(data.get("chunkX", 0)), int(data.get("chunkZ", 0)), data.get("blocks", []))
			# expande em block_update também
			for b in data.get("blocks", []):
				var p = Vector3i(int(b.get("x", 0)), int(b.get("y", 0)), int(b.get("z", 0)))
				block_update.emit(p, str(b.get("block", "")))
		"timeUpdate":
			time_update.emit(int(data.get("time", 0)), bool(data.get("isDay", true)))
		"weatherUpdate":
			weather_update.emit(str(data.get("weather", "clear")))
		"chat":
			chat_message.emit(data)
		"syncComplete":
			sync_complete.emit(int(data.get("chunksSent", 0)))
		"ok", "error", "pong":
			if verbose: print("[Ponte] ", line)
		_:
			if verbose: print("[Ponte] Tipo desconhecido: ", line)
			# tenta tratar como bulk se tiver blocks
			if data.has("blocks"):
				block_bulk.emit(data["blocks"])

# === API pública para enviar comandos ===

func connect_to_bridge():
	_reconnect_timer = 0.5
	connection_status.emit(false, "Conectando em %s:%d..." % [host, port])

func _try_connect():
	_reconnect_timer = reconnect_delay
	if verbose: print("[Ponte] Tentando conectar em %s:%d" % [host, port])
	var err = _tcp.connect_to_host(host, port)
	if err != OK:
		connection_status.emit(false, "Erro connect: %s" % err)
		# tenta de novo no próximo ciclo
	else:
		# Não marca como conectado ainda, espera poll retornar STATUS_CONNECTED
		connection_status.emit(false, "Conectando...")
		# aguarda alguns frames pra handshake
		await get_tree().create_timer(0.3).timeout
		if _tcp.get_status() == StreamPeerTCP.STATUS_CONNECTED:
			_connected = true
			_buffer = ""
			connection_status.emit(true, "Conectado em %s:%d" % [host, port])
			if verbose: print("[Ponte] Conectado!")

func _on_disconnected(reason: String):
	if _connected:
		print("[Ponte] Desconectado: ", reason)
	_connected = false
	connection_status.emit(false, reason + " - reconectando em %ds" % int(reconnect_delay))
	_reconnect_timer = reconnect_delay
	_tcp.disconnect_from_host()

func disconnect_from_bridge():
	auto_connect = false
	_connected = false
	_tcp.disconnect_from_host()
	connection_status.emit(false, "Desconectado")

func is_connected() -> bool:
	return _connected and _tcp.get_status() == StreamPeerTCP.STATUS_CONNECTED

func _send_dict(d: Dictionary):
	if not is_connected():
		push_warning("[Ponte] Não conectado, não enviou: %s" % JSON.stringify(d))
		return
	var line = JSON.stringify(d) + "\n"
	var err = _tcp.put_data(line.to_utf8_buffer())
	if err != OK:
		push_warning("[Ponte] Erro ao enviar: %s" % err)
	elif verbose:
		print("[Ponte] → ", line.strip_edges())

# Conveniências (mesmo protocolo da Unity)

func send_set_block(pos: Vector3i, block_id: String):
	_send_dict({"type": "setBlock", "x": pos.x, "y": pos.y, "z": pos.z, "block": block_id})

func send_delete_block(pos: Vector3i):
	send_set_block(pos, "minecraft:air")

func send_paint(pos: Vector3i, color: Color, material: String = "concrete"):
	var hex = "#%02X%02X%02X" % [int(color.r * 255), int(color.g * 255), int(color.b * 255)]
	_send_dict({"type": "setBlockColor", "x": pos.x, "y": pos.y, "z": pos.z, "color": hex, "material": material})

func send_fill(a: Vector3i, b: Vector3i, block_id: String):
	_send_dict({"type": "fill", "from": {"x": a.x, "y": a.y, "z": a.z}, "to": {"x": b.x, "y": b.y, "z": b.z}, "block": block_id})

func send_time(time: int):
	_send_dict({"type": "setTime", "time": time})

func send_weather(weather: String): # clear, rain, thunder
	_send_dict({"type": "setWeather", "weather": weather})

func send_teleport(pos: Vector3):
	_send_dict({"type": "teleport", "x": pos.x, "y": pos.y, "z": pos.z})

func send_chat(msg: String, from: String = "Godot"):
	_send_dict({"type": "chat", "message": msg, "from": from})

func send_command(cmd: String):
	_send_dict({"type": "command", "command": cmd})
