package org.luaj.vm2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.luaj.vm2.lib.VarArgFunction;

/**
 * A class: the table {@code class.register} returns. Holds the class's fields and methods (its base's copied in),
 * answers the system fields ({@link LuaClassManager#CLASSNAME_KEY} and the others), and is the metatable of its
 * instances.
 */
public class ClassLuaTable extends LuaTable {

	/** The manager the class is registered with; it makes the class's instances. */
	protected LuaClassManager classManager;

	private String className;
	private LuaString classNameLua;
	private String shortName;
	private LuaString shortNameLua;
	private String baseClassName;
	private LuaString baseClassNameLua;

	private ClassLuaTable classTable;
	private ClassLuaTable baseClassTable;

	/** {@link LuaClassManager#CONSTRUCTOR_KEY} as a Lua string, to recognize writes to it without converting keys. */
	private static final LuaString CONSTRUCTOR_KEY = LuaString.valueOf(LuaClassManager.CONSTRUCTOR_KEY);

	/**
	 * The class's constructor: the {@link LuaClassManager#CONSTRUCTOR_KEY} stored in the table (its own, or copied from
	 * its base), kept in step by {@link #hashset}; or, while the table holds none, the class's own default, which does
	 * nothing. Read directly by {@link #getConstructor} and for {@code __}, so neither looks the table up.
	 */
	private LuaValue constructor = new DefaultConstructor();

	/** The keys this class defines itself (its registered table's, and those set on it since); the rest it inherits. */
	private final Set<LuaValue> ownKeys = new HashSet<LuaValue>();

	/** The classes registered with this one as their base. */
	private final List<ClassLuaTable> subclasses = new ArrayList<ClassLuaTable>();

	/** While {@link LuaClassManager#registerClass} fills the class, writes only store. */
	private boolean registering = true;

	/** Set while the manager stores: the write is only stored ({@link #store}). */
	private boolean storing;

	/**
	 * Creates an empty class; {@link LuaClassManager#registerClass} fills and registers it.
	 *
	 * @param classManager   the manager registering it
	 * @param className      its name
	 * @param baseClassTable its base class, or {@code null} for none
	 */
	public ClassLuaTable(LuaClassManager classManager, String className, ClassLuaTable baseClassTable) {
		this.classManager = classManager;

		this.classTable = this;
		setClassName(className);

		this.baseClassTable = baseClassTable;
		setBaseClassName(baseClassTable == null ? "" : baseClassTable.getClassName());
	}

	/** The system fields come from the class itself, whatever the table holds under their names. */
	@Override
	protected LuaValue hashget(LuaValue key) {
		if (key.isstring()) {
			switch(key.tojstring()) {
				// Fields
				case LuaClassManager.CLASSNAME_KEY:
					return classNameLua;
				case LuaClassManager.CLASSNAME_SHORT_KEY:
					return shortNameLua;
				case LuaClassManager.CLASSNAME_BASE_KEY:
					return baseClassNameLua;
				case LuaClassManager.CLASSTABLE_KEY:
					return classTable;
				case LuaClassManager.CLASSTABLE_BASE_KEY:
					return baseClassTable == null ? NIL : baseClassTable; // nil, not Java null, for a root class
				// Methods
				case LuaClassManager.CONSTRUCTOR_KEY:
					return constructor;

				// Default
				default:
					break;
			}
			
		}
		return super.hashget(key);
	}

	/** @return the class's name, as registered */
	public String getClassName() {
		return className;
	}

	void setClassName(String className) {
		this.className = className;
		this.classNameLua = LuaValue.valueOf(this.className);
		this.set(LuaClassManager.CLASSNAME_KEY, this.classNameLua);
		this.shortName = LuaClassManager.shortName(className);
		this.shortNameLua = LuaValue.valueOf(this.shortName);
		this.set(LuaClassManager.CLASSNAME_SHORT_KEY, this.shortNameLua);
	}

	/** @return the last part of the class's name, after its last dot; the whole name if it has none */
	public String getShortName() {
		return shortName;
	}

	/** @return the base class's name, {@code ""} for a class with no base */
	public String getBaseClassName() {
		return baseClassName;
	}

	void setBaseClassName(String baseClassName) {
		this.baseClassName = baseClassName;
		this.baseClassNameLua = LuaValue.valueOf(this.baseClassName);
		this.set(LuaClassManager.CLASSNAME_BASE_KEY, this.baseClassNameLua);
	}

	/**
	 * Makes a new instance of this class, running its constructor without arguments.
	 *
	 * @return the new instance
	 */
	public InstanceLuaTable createInstance() {
		return createInstance(NONE);
	}

	/**
	 * Makes a new instance of this class, its constructor given {@code args} ({@link LuaClassManager#newInstance}).
	 *
	 * @param args the constructor's arguments (after the instance)
	 * @return the new instance
	 */
	public InstanceLuaTable createInstance(Varargs args) {
		return classManager.newInstance(this, args);
	}

	/**
	 * The constructor this class's instances run: its {@link LuaClassManager#CONSTRUCTOR_KEY}, its own or the one it
	 * inherits (kept up to date by the manager), or else the class's own default, which does nothing (a field read: no
	 * table lookup, none up the hierarchy).
	 *
	 * @return the constructor, never nil
	 */
	public LuaValue getConstructor() {
		return constructor;
	}

	/**
	 * Every write of a non-integer key ends here ({@code set}, {@code rawset}, Lua's and LuaJC's). Once the class is
	 * registered, a write is a change of the class's member, which the manager carries down its subclasses
	 * ({@link LuaClassManager#setMember}). Otherwise (registering, or the manager storing) it stores as any table
	 * does, and keeps {@link #getConstructor} in step with what's stored under {@link LuaClassManager#CONSTRUCTOR_KEY}:
	 * a constructor set there is the class's, and nil gives it a new default of its own.
	 */
	@Override
	public void hashset(LuaValue key, LuaValue value) {
		if (!registering && !storing) {
			classManager.setMember(this, key, value);
			return;
		}
		super.hashset(key, value);
		if (CONSTRUCTOR_KEY.raweq(key)) {
			constructor = value.isnil() ? new DefaultConstructor() : value;
		}
	}

	/**
	 * Stores a value without handling it as a change of member ({@link #hashset}): what the manager uses to fill and
	 * update the class.
	 */
	void store(LuaValue key, LuaValue value) {
		boolean was = storing;
		storing = true;
		try {
			rawset(key, value);
		} finally {
			storing = was;
		}
	}

	/** What the table itself holds under a key: for the constructor, nil when the class only has its default. */
	LuaValue stored(LuaValue key) {
		return CONSTRUCTOR_KEY.raweq(key) ? super.hashget(key) : rawget(key);
	}

	/** Called by the manager when the class is registered: writes are member changes from now on. */
	void finishRegistering() {
		registering = false;
	}

	/** @return the keys this class defines itself */
	Set<LuaValue> ownKeys() {
		return ownKeys;
	}

	/** @return the classes registered with this one as their base */
	List<ClassLuaTable> subclasses() {
		return subclasses;
	}

	/** @return this class */
	public ClassLuaTable getClassTable() {
		return this.classTable;
	}

	/** @return the base class, or {@code null} for a class with no base */
	public ClassLuaTable getBaseClassTable() {
		return this.baseClassTable;
	}

	/** A constructor that does nothing: each class's own default ({@link #getConstructor}). */
	private static final class DefaultConstructor extends VarArgFunction {

		@Override
		public Varargs invoke(Varargs args) {
			return NONE;
		}

	}

	// Calling the class, {@code Class(...)}, makes an instance: the arguments go to its constructor. Every call from
	// Lua (interpreted, LuaJC, pcall, tail calls) reaches one of these, so the class needs no metatable for it.

	/** {@code Class()}: a new instance, its constructor given no arguments. */
	@Override
	public LuaValue call() {
		return createInstance(NONE);
	}

	/** {@code Class(a)}: a new instance, its constructor given {@code a}. */
	@Override
	public LuaValue call(LuaValue a) {
		return createInstance(a);
	}

	/** {@code Class(a, b)}: a new instance, its constructor given {@code a, b}. */
	@Override
	public LuaValue call(LuaValue a, LuaValue b) {
		return createInstance(varargsOf(a, b));
	}

	/** {@code Class(a, b, c)}: a new instance, its constructor given {@code a, b, c}. */
	@Override
	public LuaValue call(LuaValue a, LuaValue b, LuaValue c) {
		return createInstance(varargsOf(a, b, c));
	}

	/** {@code Class(...)}: a new instance, its constructor given the arguments. */
	@Override
	public Varargs invoke(Varargs args) {
		return createInstance(args);
	}

}
