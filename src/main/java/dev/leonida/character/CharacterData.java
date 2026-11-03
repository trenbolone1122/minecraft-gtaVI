package dev.leonida.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.leonida.LeonidaMod;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Server-only saved state of the characters that are not being played right now:
 * where they stand, their inventory, health and hunger. Index = character (JASON=0, LUCIA=1).
 */
public record CharacterData(List<Slot> slots) {
	public record Slot(boolean valid, String dimension, double x, double y, double z, float yRot, float xRot,
			float health, int food, float saturation, int selected, List<ItemStackWithSlot> items) {
		public static final Slot EMPTY = new Slot(false, "minecraft:overworld", 0, 0, 0, 0, 0, 20f, 20, 5f, 0, List.of());

		public static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("valid").forGetter(Slot::valid),
				Codec.STRING.fieldOf("dimension").forGetter(Slot::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Slot::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Slot::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Slot::z),
				Codec.FLOAT.fieldOf("y_rot").forGetter(Slot::yRot),
				Codec.FLOAT.fieldOf("x_rot").forGetter(Slot::xRot),
				Codec.FLOAT.fieldOf("health").forGetter(Slot::health),
				Codec.INT.fieldOf("food").forGetter(Slot::food),
				Codec.FLOAT.fieldOf("saturation").forGetter(Slot::saturation),
				Codec.INT.fieldOf("selected").forGetter(Slot::selected),
				ItemStackWithSlot.CODEC.listOf().fieldOf("items").forGetter(Slot::items)
		).apply(i, Slot::new));
	}

	public static final CharacterData DEFAULT = new CharacterData(List.of(Slot.EMPTY, Slot.EMPTY));

	public static final Codec<CharacterData> CODEC = Slot.CODEC.listOf().xmap(CharacterData::new, CharacterData::slots);

	public static AttachmentType<CharacterData> TYPE;

	static void register() {
		TYPE = AttachmentRegistry.create(LeonidaMod.id("character_data"), b -> b
				.initializer(() -> DEFAULT)
				.persistent(CODEC)
				.copyOnDeath());
	}

	public static CharacterData get(Player p) {
		return p.getAttachedOrCreate(TYPE);
	}

	public Slot slot(int c) {
		return c >= 0 && c < slots.size() ? slots.get(c) : Slot.EMPTY;
	}

	public CharacterData with(int c, Slot s) {
		List<Slot> l = new ArrayList<>(slots);
		while (l.size() <= c) l.add(Slot.EMPTY);
		l.set(c, s);
		return new CharacterData(List.copyOf(l));
	}
}
