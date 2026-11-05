package org.luaj.vm2;

/**
 * An instance of a class: what calling the class (or {@code new}) returns. Its own fields are its own; its metatable
 * is its class, so methods and the class's fields are found there. Setting a field the class has makes the
 * instance's own, which masks the class's for this instance only, nil included: an instance set to nil reads nil,
 * not the class's value. The system fields ({@link LuaClassManager#isSystemKey}) can't be set, so they can't be
 * masked.
 */
public class InstanceLuaTable extends LuaTable {

	/**
	 * What an instance holds for a member of its class it was set to nil: Lua tables can't hold nil, and deleting the
	 * key would let the class's value show through. It reads as nil everywhere ({@link #get}, {@link #rawget},
	 * {@link #next}) and never leaves this class.
	 */
	private static final LuaValue MASKED_NIL = new LuaTable();

	/** The instance's class. */
	ClassLuaTable classTable;

	/**
	 * Creates an instance of a class; {@link LuaClassManager#newInstance} gives it its metatable and runs the constructor.
	 *
	 * @param classTable its class
	 */
	public InstanceLuaTable(ClassLuaTable classTable) {
		this.classTable = classTable;
	}

	/** @return the instance's class */
	public ClassLuaTable getClassTable() {
		return classTable;
	}

	/**
	 * Stores as any table does, but: a system field can't be set (it would mask the class's), as on a class; and nil
	 * for a member the class has is kept as a masked nil, so the instance reads nil rather than the class's value (nil
	 * for a key the class doesn't have just removes it). Every write of a non-integer key ends here ({@code set},
	 * {@code rawset}).
	 *
	 * @throws LuaError if {@code key} is a system field
	 */
	@Override
	public void hashset(LuaValue key, LuaValue value) {
		if (LuaClassManager.isSystemKey(key)) {
			throw new LuaError("instance of '" + classTable.getClassName() + "': " + key.tojstring() + " can't be set");
		}
		if (value.isnil() && !classTable.rawget(key).isnil()) {
			value = MASKED_NIL;
		}
		super.hashset(key, value);
	}

	/**
	 * A field's value as Lua reads it ({@code obj.f}, {@code obj:f()}): the instance's own, nil for a masked nil, or
	 * else the class's through its {@code __index}. One lookup in the instance, as for any table.
	 */
	@Override
	public LuaValue get(LuaValue key) {
		LuaValue value = super.rawget(key);
		if (value == MASKED_NIL) {
			return NIL;
		}
		if (!value.isnil()) {
			return value;
		}
		LuaValue index = metatag(INDEX);
		if (index.isnil()) {
			return NIL;
		}
		return index.isfunction() ? index.call(this, key) : index.get(key);
	}

	/** The instance's own value for a key, a masked nil reading as nil. */
	@Override
	public LuaValue rawget(LuaValue key) {
		LuaValue value = super.rawget(key);
		return value == MASKED_NIL ? NIL : value;
	}

	/** Iterates the instance's own fields ({@code pairs}, {@code next}), leaving out masked nils. */
	@Override
	public Varargs next(LuaValue key) {
		Varargs entry = super.next(key);
		while (entry.arg(2) == MASKED_NIL) {
			entry = super.next(entry.arg1());
		}
		return entry;
	}

}
