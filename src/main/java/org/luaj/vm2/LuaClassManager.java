package org.luaj.vm2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Registers and looks up classes for one {@link org.luaj.vm2.lib.ClassLib}: makes class tables from the tables
 * scripts register, and instance tables of them. Subclass it to make class or instance tables of your own
 * ({@link #createClassTable}, {@link #createInstanceTable}).
 */
public class LuaClassManager {

	// The system's fields on class tables (and through __index, on instances). Double-underscore lowercase, as
	// Lua names its metatable fields: a class table is its instances' metatable.
	/** The class's name, as registered (case-sensitive), e.g. {@code "weapon.pistol"}. */
	public static final String CLASSNAME_KEY = "__classname";
	/** The last part of the class's name, after its last dot ({@code "pistol"}); the whole name if it has none. */
	public static final String CLASSNAME_SHORT_KEY = "__shortname";
	/** The base class's {@link #CLASSNAME_KEY}, "" for a class with no base. */
	public static final String CLASSNAME_BASE_KEY = "__basename";
	/** The class table itself. */
	public static final String CLASSTABLE_KEY = "__class";
	/** The base class table, nil for a class with no base. */
	public static final String CLASSTABLE_BASE_KEY = "__base";
	/**
	 * The class's constructor: a method run on each new instance, given the arguments it was made with
	 * ({@code Class(...)}, {@code new(Class, ...)}). Defined as {@code __ = function(self, ...) end}, or
	 * {@code function C:__(...) end}. Copied into subclasses like any other method: a class without its own has its
	 * nearest ancestor's. A class with none at all answers its own default, one that does nothing
	 * ({@link ClassLuaTable#getConstructor}); so every class has one, and {@code super.__} can always be called from
	 * a class with a base.
	 */
	public static final String CONSTRUCTOR_KEY = "__";

	/** Keys the library keeps on every class: set at registration only (a later write is an error), never inherited. */
	private static final Set<LuaValue> SYSTEM_KEYS = new HashSet<LuaValue>(Arrays.asList(new LuaValue[] {
			LuaValue.valueOf(CLASSNAME_KEY), LuaValue.valueOf(CLASSNAME_SHORT_KEY), LuaValue.valueOf(CLASSNAME_BASE_KEY),
			LuaValue.valueOf(CLASSTABLE_KEY), LuaValue.valueOf(CLASSTABLE_BASE_KEY) }));
	/** {@code __index}: each class's own (itself), never inherited. */
	private static final LuaValue INDEX_KEY = LuaValue.INDEX;
	/** {@link #CONSTRUCTOR_KEY} as a Lua string. */
	private static final LuaValue CONSTRUCTOR = LuaValue.valueOf(CONSTRUCTOR_KEY);

	/**
	 * A map of all loaded class tables: {@code <String className, ClassLuaTable classTable>}, by exact name
	 */
	private Map<String, ClassLuaTable> classes = new HashMap<String, ClassLuaTable>();

	/**
	 * What's wrong with a class name, if anything. Names are case-sensitive ("Dog" and "dog" are two classes) and
	 * use {@code .} as a delimiter ("weapon.pistol"), so a name can't be empty, start or end with a dot, or have
	 * two dots in a row.
	 *
	 * @param className a class name
	 * @return why it isn't a valid class name, or {@code null} if it is
	 */
	public static String checkName(String className) {
		if (className.isEmpty()) {
			return "must not be empty";
		}
		if (className.startsWith(".")) {
			return "must not start with '.'";
		}
		if (className.endsWith(".")) {
			return "must not end with '.'";
		}
		if (className.contains("..")) {
			return "must not have two '.' in a row";
		}
		return null;
	}

	/**
	 * The short name of a class name: its last part, after its last dot ({@code "weapon.pistol"} gives
	 * {@code "pistol"}), or the whole name if it has none. {@link #checkName} guarantees there is a last part.
	 *
	 * @param className a valid class name
	 * @return its short name
	 */
	public static String shortName(String className) {
		return className.substring(className.lastIndexOf('.') + 1);
	}

	/**
	 * Makes the table of a new class; {@link #registerClass} fills it. Override it to use a subclass of
	 * {@link ClassLuaTable}.
	 *
	 * @param className      the class's name, already checked and not registered yet
	 * @param baseClassTable its base class, or {@code null} for none
	 * @return the new class table
	 */
	protected ClassLuaTable createClassTable(String className, ClassLuaTable baseClassTable) {
		ClassLuaTable classLuaTable = new ClassLuaTable(this, className, baseClassTable);
		return classLuaTable;
	}

	/**
	 * Makes {@code table} a class and registers it, its base the one its {@link #CLASSNAME_BASE_KEY} names (if any):
	 * {@link #registerClass(LuaValue, LuaValue)} with no base given.
	 *
	 * @param table the class's name, base name and members
	 * @return the new class table
	 * @throws LuaError as {@link #registerClass(LuaValue, LuaValue)}
	 */
	public LuaTable registerClass(LuaTable table) {
		return registerClass(table, LuaValue.NIL);
	}

	/**
	 * Makes a class and registers it ({@code class.register(classDef [, base])}).
	 * <p>
	 * {@code classDef} is either a table with a {@link #CLASSNAME_KEY} string and the class's members, or just a class
	 * name: an empty class (no members; its constructor the default, which does nothing). The name must be valid
	 * ({@link #checkName}) and not registered yet.
	 * <p>
	 * {@code base} is the base class: a registered class's name, or a class registered here; nil for none given. When
	 * given, it's the base whatever the table's {@link #CLASSNAME_BASE_KEY} says; when not, the base is the class that
	 * names (it must be registered), or none.
	 * <p>
	 * The new class holds the table's members as its own (its functions become this class's methods,
	 * {@link ClassMethod}) and copies of its base's other members ({@link #updateInheritance}); later writes to it keep
	 * that up to date down its subclasses ({@link #setMember}). The table itself is only read.
	 *
	 * @param classDef the class's table (name, base name and members), or its name
	 * @param base     the base class or its name, or nil
	 * @return the new class table
	 * @throws LuaError if {@code classDef} isn't a table or a name; the name is missing, not a string, invalid or
	 *                  already registered; {@code base} isn't nil, a name or a class registered here; the base named
	 *                  isn't registered or isn't a string; or the constructor isn't a function
	 */
	public LuaTable registerClass(LuaValue classDef, LuaValue base) {
		// The class's table: the one given, or for a name alone, an empty one with that name
		LuaTable table;
		if (classDef.istable()) {
			table = (LuaTable) classDef;
		} else if (classDef.isstring() && !classDef.isnumber()) {
			table = new LuaTable();
			table.rawset(CLASSNAME_KEY, classDef);
		} else {
			throw new LuaError("class must be a table or a class name, got " + classDef.typename());
		}

		// Register class portion
		LuaValue name = table.get(CLASSNAME_KEY);
		if (!name.isstring() || name.isnumber()) {
			throw new LuaError("class " + CLASSNAME_KEY + " must be a string, got " + name.typename());
		}
		String className = name.tojstring();
		String invalid = checkName(className);
		if (invalid != null) {
			throw new LuaError("class " + CLASSNAME_KEY + " '" + className + "' " + invalid);
		}
		if (classes.containsKey(className)) {
			throw new LuaError("class '" + className + "' is already registered");
		}

		// The base: the one given (a name or a class registered here), or else the one the table names (if it does)
		ClassLuaTable baseClassTable = null;
		LuaValue baseName = base.isnil() ? table.get(CLASSNAME_BASE_KEY) : base;
		if (base instanceof ClassLuaTable) {
			baseClassTable = (ClassLuaTable) base;
			if (classes.get(baseClassTable.getClassName()) != baseClassTable) {
				throw new LuaError("class '" + className + "': base class '" + baseClassTable.getClassName()
						+ "' isn't registered with this class library");
			}
		} else if (!base.isnil() && (!base.isstring() || base.isnumber())) {
			throw new LuaError("class '" + className + "': base class must be a class or a class name, got " + base.typename());
		} else if (!baseName.isnil()) {
			if (!baseName.isstring() || baseName.isnumber()) {
				throw new LuaError("class " + CLASSNAME_BASE_KEY + " must be a string, got " + baseName.typename());
			}
			ClassLuaTable named = getClassTable(baseName.tojstring());
			if (named == null) {
				throw new LuaError("class '" + className + "': base class '" + baseName.tojstring() + "' isn't registered");
			}
			baseClassTable = named;
		}

		// A constructor must be a function (checked before anything is registered)
		LuaValue constructor = table.rawget(CONSTRUCTOR_KEY);
		if (!constructor.isnil() && !constructor.isfunction()) {
			throw new LuaError("class '" + className + "': " + CONSTRUCTOR_KEY + " must be a function, got " + constructor.typename());
		}

		ClassLuaTable classTable = createClassTable(className, baseClassTable);
		classes.put(className, classTable); // Save class table to classman
		if (baseClassTable != null) {
			baseClassTable.subclasses().add(classTable);
		}

		// The table's members are the class's own; its functions become this class's methods (see ClassMethod: super)
		for (LuaValue key : table.keys()) {
			if (SYSTEM_KEYS.contains(key) || INDEX_KEY.raweq(key)) continue; // the class answers these itself
			classTable.ownKeys().add(key);
			classTable.store(key, ClassMethod.wrap(table.rawget(key), classTable));
		}

		updateInheritance(classTable);

		classTable.store(INDEX_KEY, classTable); // Instances (whose metatable is this class) find its methods and fields through it
		classTable.finishRegistering();
		return classTable;
	}

	/**
	 * Gives a class being registered what it inherits: every member of its base it doesn't define itself, one key at
	 * a time ({@link #updateMethodInheritance}, {@link #updateConstructorInheritance}).
	 *
	 * @param classTable the class being registered
	 */
	private void updateInheritance(ClassLuaTable classTable) {
		ClassLuaTable base = classTable.getBaseClassTable();
		if (base == null) {
			return;
		}
		for (LuaValue key : base.keys()) {
			if (!inherited(key)) continue;
			if (CONSTRUCTOR.raweq(key)) {
				updateConstructorInheritance(classTable);
			} else {
				updateMethodInheritance(classTable, key);
			}
		}
	}

	/**
	 * Brings one member up to date in a class and down its subclasses: a class that defines the key keeps its own
	 * value, one that doesn't takes its base's (or nil), and so on through the subclasses that don't define it either.
	 *
	 * @param classTable the class where the member changed (or is inherited for the first time)
	 * @param key        the member's key
	 */
	private void updateMethodInheritance(ClassLuaTable classTable, LuaValue key) {
		if (!classTable.ownKeys().contains(key)) {
			ClassLuaTable base = classTable.getBaseClassTable();
			classTable.store(key, base == null ? LuaValue.NIL : base.stored(key));
		}
		for (ClassLuaTable subclass : classTable.subclasses()) {
			if (!subclass.ownKeys().contains(key)) {
				updateMethodInheritance(subclass, key);
			}
		}
	}

	/**
	 * {@link #updateMethodInheritance} for {@link #CONSTRUCTOR_KEY}. A class that ends up with none answers its own
	 * default ({@link ClassLuaTable#getConstructor}), which is never copied.
	 *
	 * @param classTable the class where the constructor changed (or is inherited for the first time)
	 */
	private void updateConstructorInheritance(ClassLuaTable classTable) {
		updateMethodInheritance(classTable, CONSTRUCTOR);
	}

	/**
	 * A write to a registered class ({@code C.f = ...}, {@code rawset}): the class defines that member from now on
	 * (nil: no longer, so it inherits its base's again), a function becoming its method, and its subclasses that
	 * don't define it follow. System fields can't be set; integer keys and {@code __index} are only stored.
	 *
	 * @param classTable the class written to
	 * @param key        the key
	 * @param value      the value, nil to remove the class's own
	 * @throws LuaError if {@code key} is a system field
	 */
	void setMember(ClassLuaTable classTable, LuaValue key, LuaValue value) {
		if (SYSTEM_KEYS.contains(key)) {
			throw new LuaError("class '" + classTable.getClassName() + "': " + key.tojstring() + " can't be set");
		}
		if (key.isinttype() || INDEX_KEY.raweq(key)) {
			classTable.store(key, value);
			return;
		}
		if (value.isnil()) {
			classTable.ownKeys().remove(key);
		} else {
			classTable.ownKeys().add(key);
			classTable.store(key, ClassMethod.wrap(value, classTable));
		}
		if (CONSTRUCTOR.raweq(key)) {
			updateConstructorInheritance(classTable);
		} else {
			updateMethodInheritance(classTable, key);
		}
	}

	/**
	 * Whether a key is one of the system fields the library keeps ({@link #CLASSNAME_KEY}, {@link #CLASSNAME_SHORT_KEY},
	 * {@link #CLASSNAME_BASE_KEY}, {@link #CLASSTABLE_KEY}, {@link #CLASSTABLE_BASE_KEY}): set on neither classes nor
	 * instances.
	 *
	 * @param key a key
	 * @return whether it's a system field
	 */
	public static boolean isSystemKey(LuaValue key) {
		return SYSTEM_KEYS.contains(key);
	}

	/** Whether a base's key is inherited: everything but the system fields and {@code __index}. */
	private static boolean inherited(LuaValue key) {
		return !SYSTEM_KEYS.contains(key) && !INDEX_KEY.raweq(key);
	}

	/**
	 * Makes a new instance of a class: its table ({@link #createInstanceTable}), with the class as its metatable, on
	 * which the class's constructor ({@link ClassLuaTable#getConstructor}), if it has one, then runs with
	 * {@code args}.
	 *
	 * @param classTable the instance's class
	 * @param args       the constructor's arguments (after the instance)
	 * @return the new instance
	 */
	public InstanceLuaTable newInstance(ClassLuaTable classTable, Varargs args) {
		InstanceLuaTable instanceTable = createInstanceTable(classTable);
		instanceTable.setmetatable(classTable);
		LuaValue constructor = classTable.getConstructor();
		if (!constructor.isnil()) {
			constructor.invoke(LuaValue.varargsOf(instanceTable, args));
		}
		return instanceTable;
	}

	/**
	 * Creates the table of a new instance of a class, nothing more: {@link #newInstance} gives it its metatable and
	 * runs the constructor. Override it to use a subclass of {@link InstanceLuaTable}.
	 *
	 * @param classTable the instance's class
	 * @return the new instance table
	 */
	protected InstanceLuaTable createInstanceTable(ClassLuaTable classTable) {
		return new InstanceLuaTable(classTable);
	}

	/**
	 * The registered class of exactly that name (case-sensitive).
	 *
	 * @param className the class's name
	 * @return the class, or {@code null} if none is registered by that name
	 */
	public ClassLuaTable getClassTable(String className) {
		return this.classes.get(className);
	}

	/**
	 * The names of all registered classes, sorted.
	 *
	 * @return the names (a new list)
	 */
	public List<String> getClassNames() {
		return getClassNames(null);
	}

	/**
	 * The names of the registered classes under a dotted prefix, sorted: {@code "game.weapon"} gives
	 * {@code "game.weapon"} itself (if registered) and {@code "game.weapon.gun"}, but not {@code "game.weaponry"}.
	 *
	 * @param prefix the prefix, or {@code null} (or empty) for all classes
	 * @return the names (a new list)
	 */
	public List<String> getClassNames(String prefix) {
		List<String> names = new ArrayList<String>();
		for (String name : classes.keySet()) {
			if (prefix == null || prefix.isEmpty() || name.equals(prefix) || name.startsWith(prefix + ".")) {
				names.add(name);
			}
		}
		Collections.sort(names);
		return names;
	}

	/**
	 * A class's subclasses: those registered with it as their base, or with {@code all}, every class below it
	 * (depth first, each subclass followed by its own).
	 *
	 * @param classTable the class
	 * @param all        whether to include subclasses' subclasses, all the way down
	 * @return the subclasses, in the order they were registered (a new list)
	 */
	public List<ClassLuaTable> getSubclasses(ClassLuaTable classTable, boolean all) {
		List<ClassLuaTable> out = new ArrayList<ClassLuaTable>();
		for (ClassLuaTable subclass : classTable.subclasses()) {
			out.add(subclass);
			if (all) {
				out.addAll(getSubclasses(subclass, true));
			}
		}
		return out;
	}

	/**
	 * Whether a class is another, or extends it (directly or further up).
	 *
	 * @param classTable the class asked about
	 * @param baseClass  the class it may be or extend
	 * @param exact      only whether it's that very class
	 * @return whether {@code classTable} is {@code baseClass} or ({@code exact} false) one of its subclasses
	 */
	public boolean isSubclass(ClassLuaTable classTable, ClassLuaTable baseClass, boolean exact) {
		if (exact) {
			return classTable == baseClass;
		}
		for (ClassLuaTable c = classTable; c != null; c = c.getBaseClassTable()) {
			if (c == baseClass) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether an instance is of a class, or of one of its subclasses.
	 *
	 * @param instance  the instance
	 * @param classTable the class
	 * @param exact     only whether its class is that very class
	 * @return whether {@code instance}'s class is {@code classTable} or ({@code exact} false) one of its subclasses
	 */
	public boolean isInstance(InstanceLuaTable instance, ClassLuaTable classTable, boolean exact) {
		return isSubclass(instance.getClassTable(), classTable, exact);
	}

}
