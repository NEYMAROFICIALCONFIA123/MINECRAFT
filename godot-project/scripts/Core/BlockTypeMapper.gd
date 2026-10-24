extends Resource
class_name BlockTypeMapper
## Mapeia block IDs do Minecraft para cores/materiais no Godot
## Crie um .tres com Create Resource > BlockTypeMapper

@export var entries: Array[BlockEntry] = []

var _lookup: Dictionary = {}

func _init():
	rebuild_lookup()

func rebuild_lookup():
	_lookup.clear()
	for e in entries:
		if e.block_id != "":
			_lookup[e.block_id] = e

func get_entry(block_id: String) -> BlockEntry:
	if _lookup.is_empty():
		rebuild_lookup()
	if _lookup.has(block_id):
		return _lookup[block_id]
	# fallback sem namespace
	var short_id = block_id.split(":")[-1] if ":" in block_id else block_id
	for k in _lookup.keys():
		if k.ends_with(short_id):
			return _lookup[k]
	return null

func color_for(block_id: String) -> Color:
	var e = get_entry(block_id)
	if e != null:
		return e.color
	# Heurísticas
	if "grass" in block_id: return Color(0.35, 0.6, 0.2)
	if "dirt" in block_id: return Color(0.55, 0.35, 0.2)
	if "stone" in block_id: return Color(0.6, 0.6, 0.6)
	if "sand" in block_id: return Color(0.85, 0.8, 0.5)
	if "wood" in block_id or "log" in block_id or "plank" in block_id: return Color(0.65, 0.45, 0.2)
	if "leaves" in block_id or "leaf" in block_id: return Color(0.2, 0.5, 0.15)
	if "water" in block_id: return Color(0.2, 0.4, 0.9, 0.6)
	if "lava" in block_id: return Color(0.9, 0.3, 0.05)
	if "glass" in block_id: return Color(0.9, 0.95, 1.0, 0.35)
	if "wool" in block_id or "concrete" in block_id or "terracotta" in block_id:
		for k in VANILLA_PALETTE.keys():
			if k in block_id:
				return VANILLA_PALETTE[k]
	return Color.GRAY

func is_transparent(block_id: String) -> bool:
	var e = get_entry(block_id)
	if e != null:
		return e.transparent
	return "glass" in block_id or "water" in block_id or block_id == "minecraft:air"

func add_default_entries():
	entries.clear()
	var commons = [
		"minecraft:stone", "minecraft:dirt", "minecraft:grass_block", "minecraft:oak_planks",
		"minecraft:oak_log", "minecraft:glass", "minecraft:sand", "minecraft:gravel",
		"minecraft:water", "minecraft:lava", "minecraft:bedrock", "minecraft:bricks",
		"minecraft:white_concrete", "minecraft:red_concrete", "minecraft:blue_concrete",
		"minecraft:yellow_concrete", "minecraft:green_concrete", "minecraft:black_concrete"
	]
	for id in commons:
		var e = BlockEntry.new()
		e.block_id = id
		e.color = color_for(id)
		entries.append(e)

# Paleta vanilla 16 cores (concrete)
const VANILLA_PALETTE: Dictionary = {
	"white": Color(0.91, 0.92, 0.92),
	"light_gray": Color(0.70, 0.70, 0.70),
	"gray": Color(0.39, 0.39, 0.39),
	"black": Color(0.10, 0.10, 0.10),
	"brown": Color(0.45, 0.28, 0.16),
	"red": Color(0.63, 0.15, 0.13),
	"orange": Color(0.89, 0.47, 0.13),
	"yellow": Color(0.99, 0.77, 0.15),
	"lime": Color(0.44, 0.73, 0.10),
	"green": Color(0.23, 0.36, 0.07),
	"cyan": Color(0.08, 0.47, 0.53),
	"light_blue": Color(0.15, 0.54, 0.78),
	"blue": Color(0.17, 0.18, 0.56),
	"purple": Color(0.39, 0.13, 0.61),
	"magenta": Color(0.66, 0.19, 0.62),
	"pink": Color(0.84, 0.40, 0.56),
}

## Sub-resource
class BlockEntry:
	extends Resource
	@export var block_id: String = ""
	@export var color: Color = Color.WHITE
	@export var metallic: float = 0.0
	@export var roughness: float = 0.8
	@export var transparent: bool = false
	@export var custom_mesh: Mesh = null
