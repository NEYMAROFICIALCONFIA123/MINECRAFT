extends Node3D
## Reconstrói o mundo Minecraft no Godot com MeshInstance3D (BoxMesh)
## Conecta nos signals do MinecraftBridgeClient (Autoload BridgeClient)

@export var bridge_client_path: NodePath = ^"/root/BridgeClient"
@export var mapper: BlockTypeMapper

@export var world_root: Node3D
@export var max_blocks: int = 200000
@export var use_pooling: bool = true

var _spawned: Dictionary = {} # Vector3i -> MeshInstance3D
var _pool: Array[MeshInstance3D] = []
var _box_mesh: BoxMesh
var _bridge: Node

func _ready():
	if world_root == null:
		world_root = self

	_box_mesh = BoxMesh.new()
	_box_mesh.size = Vector3.ONE

	_bridge = get_node_or_null(bridge_client_path)
	if _bridge == null:
		# tenta achar na cena
		_bridge = get_tree().get_first_node_in_group("bridge_client")
	if _bridge == null:
		push_warning("[Ponte] WorldBuilder sem bridge! Adicione MinecraftBridgeClient na cena ou como Autoload.")
		return

	_bridge.block_update.connect(_on_block_update)
	_bridge.block_bulk.connect(_on_block_bulk)
	_bridge.chunk_data.connect(_on_chunk_data)
	_bridge.hello.connect(func(_d): print("[Ponte] Hello recebido - pronto pra sync"))

func _exit_tree():
	if _bridge:
		if _bridge.block_update.is_connected(_on_block_update):
			_bridge.block_update.disconnect(_on_block_update)

func _on_block_update(pos: Vector3i, block_id: String):
	set_block(pos, block_id)

func _on_block_bulk(blocks: Array):
	for b in blocks:
		if b is Dictionary:
			var p = Vector3i(int(b.get("x", 0)), int(b.get("y", 0)), int(b.get("z", 0)))
			set_block(p, str(b.get("block", "")))

func _on_chunk_data(chunk_x: int, chunk_z: int, blocks: Array):
	for b in blocks:
		var p = Vector3i(int(b.get("x", 0)), int(b.get("y", 0)), int(b.get("z", 0)))
		set_block(p, str(b.get("block", "")))
	print("[Ponte] Chunk %d,%d -> %d blocos" % [chunk_x, chunk_z, blocks.size()])

## Núcleo: cria/atualiza/remove bloco

func set_block(pos: Vector3i, block_id: String):
	var is_air = block_id == "minecraft:air" or block_id == "air" or block_id == ""
	if is_air:
		if _spawned.has(pos):
			var inst = _spawned[pos] as MeshInstance3D
			_spawned.erase(pos)
			_return_to_pool(inst)
		return

	# atualiza existente
	if _spawned.has(pos):
		var existing = _spawned[pos] as MeshInstance3D
		_update_visual(existing, block_id)
		return

	if _spawned.size() >= max_blocks:
		push_warning("[Ponte] Limite de blocos atingido (%d)" % max_blocks)
		return

	var inst = _get_from_pool()
	inst.position = Vector3(pos.x, pos.y, pos.z) # 1 unidade = 1 bloco
	inst.visible = true
	world_root.add_child(inst)
	_update_visual(inst, block_id)
	_spawned[pos] = inst

func _update_visual(inst: MeshInstance3D, block_id: String):
	var col: Color = Color.GRAY
	if mapper:
		col = mapper.color_for(block_id)
	else:
		col = _fallback_color(block_id)
	var is_trans = mapper.is_transparent(block_id) if mapper else ("glass" in block_id or "water" in block_id)

	var mat := StandardMaterial3D.new()
	mat.albedo_color = col
	if is_trans:
		mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		mat.albedo_color.a = col.a if col.a < 1.0 else 0.45
	inst.material_override = mat
	inst.name = "%s @ %s" % [block_id, str(inst.position)]

func _get_from_pool() -> MeshInstance3D:
	if use_pooling and _pool.size() > 0:
		return _pool.pop_back()
	var mi = MeshInstance3D.new()
	mi.mesh = _box_mesh
	# collider para raycast
	var body = StaticBody3D.new()
	var shape = CollisionShape3D.new()
	shape.shape = BoxShape3D.new()
	shape.shape.size = Vector3.ONE
	body.add_child(shape)
	mi.add_child(body)
	return mi

func _return_to_pool(inst: MeshInstance3D):
	inst.get_parent().remove_child(inst)
	if use_pooling:
		_pool.append(inst)
	else:
		inst.queue_free()

func clear_world():
	for pos in _spawned.keys():
		_return_to_pool(_spawned[pos])
	_spawned.clear()

func has_block(pos: Vector3i) -> bool:
	return _spawned.has(pos)

func get_block_count() -> int:
	return _spawned.size()

## Utilitário de raycast da câmera

func raycast_from_camera(camera: Camera3D) -> Dictionary:
	# retorna {hit_pos: Vector3i, place_pos: Vector3i} ou {}
	if camera == null:
		return {}
	var mouse_pos = get_viewport().get_mouse_position()
	var origin = camera.project_ray_origin(mouse_pos)
	var dir = camera.project_ray_normal(mouse_pos)
	var space = get_world_3d().direct_space_state
	var query = PhysicsRayQueryParameters3D.create(origin, origin + dir * 1000.0)
	var result = space.intersect_ray(query)
	if result.is_empty():
		return {}
	var pos: Vector3 = result["position"]
	var normal: Vector3 = result["normal"]
	# hit dentro do bloco clicado
	var hit_p = pos - normal * 0.01
	var place_p = pos + normal * 0.5
	return {
		"hit_pos": Vector3i(roundi(hit_p.x), roundi(hit_p.y), roundi(hit_p.z)),
		"place_pos": Vector3i(roundi(place_p.x), roundi(place_p.y), roundi(place_p.z)),
		"normal": normal
	}

func _fallback_color(block_id: String) -> Color:
	if "grass" in block_id: return Color(0.35, 0.6, 0.2)
	if "stone" in block_id: return Color(0.6, 0.6, 0.6)
	if "sand" in block_id: return Color(0.85, 0.8, 0.5)
	if "glass" in block_id: return Color(0.9, 0.95, 1.0, 0.35)
	return Color.GRAY
