package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** LuaClassManager used from Java, without a script. */
class LuaClassManagerTest {

	private final LuaClassManager manager = new LuaClassManager();

	private ClassLuaTable register(String name, String base) {
		return (ClassLuaTable) manager.registerClass(LuaValue.valueOf(name), base == null ? LuaValue.NIL : LuaValue.valueOf(base));
	}

	@Test
	void registersAndFindsClasses() {
		LuaTable table = new LuaTable();
		table.set(LuaClassManager.CLASSNAME_KEY, "game.entity");
		ClassLuaTable entity = (ClassLuaTable) manager.registerClass(table);
		ClassLuaTable gun = register("game.gun", "game.entity");
		assertSame(entity, manager.getClassTable("game.entity"));
		assertSame(entity, gun.getBaseClassTable());
		assertEquals("gun", gun.getShortName());
		assertNull(manager.getClassTable("nope"));
	}

	@Test
	void listsAndQueriesTheHierarchy() {
		ClassLuaTable w = register("game.weapon", null);
		ClassLuaTable g = register("game.weapon.gun", "game.weapon");
		ClassLuaTable p = register("game.weapon.pistol", "game.weapon.gun");
		register("game.weaponry", null);
		assertEquals(Arrays.asList("game.weapon", "game.weapon.gun", "game.weapon.pistol", "game.weaponry"), manager.getClassNames());
		assertEquals(Arrays.asList("game.weapon", "game.weapon.gun", "game.weapon.pistol"), manager.getClassNames("game.weapon"));
		assertEquals(Arrays.asList(g), manager.getSubclasses(w, false));
		assertEquals(Arrays.asList(g, p), manager.getSubclasses(w, true));
		assertTrue(manager.isSubclass(p, w, false));
		assertFalse(manager.isSubclass(p, w, true));
		assertFalse(manager.isSubclass(w, p, false));
	}

	@Test
	void makesInstancesThatKnowTheirClass() {
		ClassLuaTable w = register("weapon", null);
		ClassLuaTable g = register("gun", "weapon");
		InstanceLuaTable i = manager.newInstance(g, LuaValue.NONE);
		assertSame(g, i.getClassTable());
		assertSame(g, i.getmetatable());
		assertTrue(manager.isInstance(i, w, false));
		assertFalse(manager.isInstance(i, w, true));
	}

	@Test
	void reportsMisuseAsLuaErrors() {
		register("taken", null);
		assertThrows(LuaError.class, () -> register("taken", null));
		assertThrows(LuaError.class, () -> register("x", "nope"));
		assertThrows(LuaError.class, () -> register("a..b", null));
		assertThrows(LuaError.class, () -> manager.registerClass(LuaValue.valueOf(5), LuaValue.NIL));
	}
}
