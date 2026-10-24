extends Camera3D
## Câmera livre estilo Minecraft (WASD + QE + Shift + botão direito)

@export var move_speed: float = 12.0
@export var fast_multiplier: float = 3.0
@export var look_speed: float = 0.003
@export var invert_y: bool = false

var _yaw: float = 0.0
var _pitch: float = 0.0
var _mouse_captured: bool = false

func _ready():
	_yaw = rotation.y
	_pitch = rotation.x

func _process(delta):
	var speed = move_speed * (fast_multiplier if Input.is_key_pressed(KEY_SHIFT) else 1.0) * delta * 4.0
	var dir = Vector3.ZERO
	var basis = global_transform.basis
	if Input.is_action_pressed("move_forward"):
		dir -= basis.z
	if Input.is_action_pressed("move_back"):
		dir += basis.z
	if Input.is_action_pressed("move_left"):
		dir -= basis.x
	if Input.is_action_pressed("move_right"):
		dir += basis.x
	if Input.is_action_pressed("move_up"):
		dir += Vector3.UP
	if Input.is_action_pressed("move_down"):
		dir -= Vector3.UP
	if dir != Vector3.ZERO:
		dir = dir.normalized()
		global_position += dir * speed * 5.0

	# scroll zoom
	# (InputEventMouseButton wheel handled in _input)

func _input(event):
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_RIGHT:
			if event.pressed:
				_mouse_captured = true
				Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
			else:
				_mouse_captured = false
				Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE)
		elif event.button_index == MOUSE_BUTTON_WHEEL_UP:
			global_position += -global_transform.basis.z * 2.0
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
			global_position += global_transform.basis.z * 2.0

	if event is InputEventMouseMotion and _mouse_captured:
		_yaw -= event.relative.x * look_speed * 80.0
		_pitch += event.relative.y * look_speed * 80.0 * (-1 if invert_y else 1) * -1
		# Godot: mouse Y invertido já
		_pitch = clampf(_pitch, deg_to_rad(-90), deg_to_rad(90))
		rotation.y = _yaw
		rotation.x = _pitch
