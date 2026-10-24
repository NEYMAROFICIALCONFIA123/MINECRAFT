extends CanvasLayer
## Chat bidirecional simples

@export var bridge_path: NodePath = ^"/root/BridgeClient"

var _bridge: Node
var _log: String = ""

@onready var _label: Label = $Panel/VBox/LogLabel if has_node("Panel/VBox/LogLabel") else null
@onready var _input: LineEdit = $Panel/VBox/HBox/Input if has_node("Panel/VBox/HBox/Input") else null

func _ready():
	_bridge = get_node_or_null(bridge_path) if bridge_path != NodePath("") else get_node_or_null("/root/BridgeClient")
	if _bridge and _bridge.has_signal("chat_message"):
		_bridge.chat_message.connect(_on_chat)
	# constrói UI mínima se não vier do .tscn
	if _label == null:
		_build_ui()

func _build_ui():
	var panel = PanelContainer.new()
	panel.name = "Panel"
	add_child(panel)
	panel.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	panel.offset_left = 10
	panel.offset_bottom = -10
	panel.offset_right = 400
	panel.offset_top = -150
	var vbox = VBoxContainer.new(); vbox.name = "VBox"; panel.add_child(vbox)
	var lbl = Label.new(); lbl.name = "LogLabel"; lbl.text = "[Chat] Conecte no Minecraft...\n"; lbl.autowrap_mode = TextServer.AUTOWRAP_WORD; vbox.add_child(lbl)
	_label = lbl
	var hbox = HBoxContainer.new(); hbox.name = "HBox"; vbox.add_child(hbox)
	var inp = LineEdit.new(); inp.name = "Input"; inp.placeholder_text = "Digite e Enter..."; inp.custom_minimum_size = Vector2(280, 0); hbox.add_child(inp)
	_input = inp
	var btn = Button.new(); btn.text = "Enviar"; btn.pressed.connect(_on_send); hbox.add_child(btn)
	_input.text_submitted.connect(func(_t): _on_send())

func _on_chat(data: Dictionary):
	var from = str(data.get("from", "MC"))
	var msg = str(data.get("message", ""))
	_log += "%s: %s\n" % [from, msg]
	if _label: _label.text = _log
	print("[Chat] %s: %s" % [from, msg])

func _on_send():
	if _input == null or _input.text.strip_edges() == "": return
	var txt = _input.text
	if _bridge: _bridge.send_chat(txt)
	_log += "Você: %s\n" % txt
	if _label: _label.text = _log
	_input.text = ""
