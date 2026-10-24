extends CanvasLayer
## UI completa da Ponte: paleta, tempo, clima, fill, teleport

@export var bridge_path: NodePath = ^"/root/BridgeClient"
@export var world_builder_path: NodePath = ^"../WorldBuilder"
@export var camera_path: NodePath = ^"../Camera3D"

var _bridge: Node
var _world: Node
var _camera: Camera3D

# Estado da ferramenta
enum Tool { PAINT, DELETE, FILL, TELEPORT }
var current_tool: Tool = Tool.PAINT
var selected_color: Color = Color.WHITE
var selected_material: String = "concrete"
var selected_block_id: String = "minecraft:white_concrete"

var _fill_start: Vector3i
var _has_fill_start: bool = false

func _ready():
	_bridge = get_node_or_null(bridge_path) if bridge_path != NodePath("") else null
	if _bridge == null:
		_bridge = get_tree().get_first_node_in_group("bridge_client")
	if _bridge == null:
		_bridge = get_node_or_null("/root/BridgeClient")
	_world = get_node_or_null(world_builder_path)
	_camera = get_node_or_null(camera_path)
	if _camera == null:
		_camera = get_viewport().get_camera_3d()

	# Constrói UI via código (sem precisar de .tscn manual)
	_build_ui()

func _build_ui():
	var panel = PanelContainer.new()
	panel.name = "PontePanel"
	add_child(panel)
	# posiciona no canto
	panel.set_anchors_preset(Control.PRESET_TOP_LEFT)
	panel.offset_left = 10
	panel.offset_top = 10
	panel.offset_right = 360
	panel.offset_bottom = 600
	panel.custom_minimum_size = Vector2(340, 500)

	var vbox = VBoxContainer.new()
	panel.add_child(vbox)

	var title = Label.new()
	title.text = "🌉 PONTE MINECRAFT → GODOT"
	title.add_theme_font_size_override("font_size", 16)
	vbox.add_child(title)

	var status = Label.new()
	status.name = "StatusLabel"
	status.text = "Status: Desconectado"
	vbox.add_child(status)
	# atualiza status via signal
	if _bridge and _bridge.has_signal("connection_status"):
		_bridge.connection_status.connect(func(connected, txt): status.text = "Status: %s | Blocos: %d" % [txt, _world.get_block_count() if _world else 0])

	var sep = HSeparator.new(); vbox.add_child(sep)

	# ferramentas
	var tool_label = Label.new(); tool_label.text = "Ferramenta (clique no mundo):"; vbox.add_child(tool_label)
	var hbox = HBoxContainer.new(); vbox.add_child(hbox)
	for t in [
		[Tool.PAINT, "🎨 Pintar"],
		[Tool.DELETE, "🗑️ Deletar"],
		[Tool.FILL, "🧱 Fill"],
		[Tool.TELEPORT, "📍 Teleport"]
	]:
		var btn = Button.new()
		btn.text = t[1]
		btn.toggle_mode = false
		btn.pressed.connect(func(): current_tool = t[0])
		hbox.add_child(btn)

	# cor
	var color_label = Label.new(); color_label.text = "Cor (pintar):"; vbox.add_child(color_label)
	var color_picker = ColorPickerButton.new()
	color_picker.name = "ColorPicker"
	color_picker.color = selected_color
	color_picker.custom_minimum_size = Vector2(200, 30)
	color_picker.color_changed.connect(func(c): selected_color = c; selected_block_id = "minecraft:white_concrete")
	vbox.add_child(color_picker)

	var mat_label = Label.new(); mat_label.text = "Material:"; vbox.add_child(mat_label)
	var mat_hbox = HBoxContainer.new(); vbox.add_child(mat_hbox)
	for m in ["concrete", "wool", "terracotta"]:
		var b = Button.new(); b.text = m
		b.pressed.connect(func(): selected_material = m)
		mat_hbox.add_child(b)

	# bloco fill
	var block_label = Label.new(); block_label.text = "Bloco para Fill:"; vbox.add_child(block_label)
	var block_hbox = HBoxContainer.new(); vbox.add_child(block_hbox)
	for b in ["stone", "glass", "oak_planks", "bricks"]:
		var id = "minecraft:" + b
		var btn = Button.new(); btn.text = b
		btn.pressed.connect(func(): selected_block_id = id)
		block_hbox.add_child(btn)

	vbox.add_child(HSeparator.new())

	# tempo
	var time_label = Label.new(); time_label.text = "Tempo:"; vbox.add_child(time_label)
	var time_hbox = HBoxContainer.new(); vbox.add_child(time_hbox)
	for t in [[1000, "🌅 Dia"], [6000, "☀️ Meio"], [13000, "🌇 Tarde"], [18000, "🌙 Noite"]]:
		var btn = Button.new(); btn.text = t[1]
		btn.pressed.connect(func(): _bridge.send_time(t[0]) if _bridge else null)
		time_hbox.add_child(btn)

	# clima
	var weather_label = Label.new(); weather_label.text = "Clima:"; vbox.add_child(weather_label)
	var weather_hbox = HBoxContainer.new(); vbox.add_child(weather_hbox)
	var w_clear = Button.new(); w_clear.text = "☀️ Limpo"; w_clear.pressed.connect(func(): _bridge.send_weather("clear") if _bridge else null); weather_hbox.add_child(w_clear)
	var w_rain = Button.new(); w_rain.text = "🌧️ Chuva"; w_rain.pressed.connect(func(): _bridge.send_weather("rain") if _bridge else null); weather_hbox.add_child(w_rain)
	var w_thunder = Button.new(); w_thunder.text = "⛈️ Tempestade"; w_thunder.pressed.connect(func(): _bridge.send_weather("thunder") if _bridge else null); weather_hbox.add_child(w_thunder)

	vbox.add_child(HSeparator.new())

	# dicas + limpar
	var tip = Label.new()
	tip.text = "Dicas: Ctrl+Click = teleportar\nShift+WASD = correr | Botão direito gira câmera"
	tip.autowrap_mode = TextServer.AUTOWRAP_WORD
	vbox.add_child(tip)

	var clear_btn = Button.new(); clear_btn.text = "Limpar Mundo Local"
	clear_btn.pressed.connect(func(): _world.clear_world() if _world else null)
	vbox.add_child(clear_btn)

	var fill_cancel = Button.new(); fill_cancel.text = "Cancelar Fill (se travado)"
	fill_cancel.pressed.connect(func(): _has_fill_start = false)
	vbox.add_child(fill_cancel)

func _input(event):
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		# ignora clique na UI
		if _is_mouse_over_ui():
			return
		_handle_click()

func _handle_click():
	if _world == null or _camera == null:
		_camera = get_viewport().get_camera_3d()
		if _camera == null: return
	if _bridge == null: return

	var res = _world.raycast_from_camera(_camera)
	if res.is_empty():
		return
	var hit_pos: Vector3i = res["hit_pos"]
	var ctrl = Input.is_key_pressed(KEY_CTRL)

	if ctrl:
		_bridge.send_teleport(Vector3(hit_pos))
		print("[Ponte] Teleport para ", hit_pos)
		return

	match current_tool:
		Tool.PAINT:
			_bridge.send_paint(hit_pos, selected_color, selected_material)
			_world.set_block(hit_pos, "minecraft:white_concrete") # preview local
		Tool.DELETE:
			_bridge.send_delete_block(hit_pos)
			_world.set_block(hit_pos, "minecraft:air")
		Tool.FILL:
			if not _has_fill_start:
				_fill_start = hit_pos
				_has_fill_start = true
				print("[Ponte] Fill início em ", _fill_start)
			else:
				_bridge.send_fill(_fill_start, hit_pos, selected_block_id)
				_has_fill_start = false
				print("[Ponte] Fill %s -> %s com %s" % [_fill_start, hit_pos, selected_block_id])
		Tool.TELEPORT:
			_bridge.send_teleport(Vector3(hit_pos))

func _is_mouse_over_ui() -> bool:
	# checa se mouse está sobre o Panel
	var panel = get_node_or_null("PontePanel")
	if panel == null: return false
	var rect = panel.get_global_rect() if panel is Control else Rect2()
	# simplificado: se viewport mouse está dentro do rect superior esquerdo 360x600
	var mp = get_viewport().get_mouse_position()
	return mp.x < 360 and mp.y < 600 and mp.x > 10 and mp.y > 10

# Chamados por UI extra se quiser expor
func set_time_slider(value: float):
	if _bridge: _bridge.send_time(int(value))
func send_chat(msg: String):
	if _bridge: _bridge.send_chat(msg)
