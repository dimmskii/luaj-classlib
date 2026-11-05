package org.luaj.vm2.lib;

import org.luaj.vm2.ClassLuaTable;
import org.luaj.vm2.ClassMethod;
import org.luaj.vm2.InstanceLuaTable;
import org.luaj.vm2.LuaClassManager;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the {@code class} library.
 */
public class ClassLib extends TwoArgFunction {
	/** The name of the library's table: the global {@code class}. */
	private static final String PACKAGE_NAME = "class";

	/** Registers and looks up this library's classes. */
	private LuaClassManager classManager;

	/** Creates the library; {@link org.luaj.vm2.Globals#load(LuaValue)} installs it. */
	public ClassLib() {
		this.classManager = new LuaClassManager();
	}

	/**
	 * Creates the library with a class manager of your own (to make class or instance tables of your own).
	 *
	 * @param classManager registers and looks up the library's classes
	 */
	public ClassLib(LuaClassManager classManager) {
		this.classManager = classManager;
	}

	/**
	 * Installs the library into {@code env}.
	 *
	 * @param modname the module name ({@code require}'s argument), unused
	 * @param env     the environment, normally the {@link org.luaj.vm2.Globals}
	 * @return the library's table
	 */
	@Override
	public LuaValue call(LuaValue modname, LuaValue env) {
		LuaTable lib = new LuaTable();
		lib.set("get", new GetFunction());
		lib.set("register", new RegisterFunction());
		lib.set("list", new ListFunction());
		lib.set("subclasses", new SubclassesFunction());
		lib.set("extends", new ExtendsFunction());
		env.set(PACKAGE_NAME, lib);
		env.set("isclass", new IsClassFunction());
		env.set("isobj", new IsObjFunction());
		env.set("instanceof", new InstanceofFunction());
		env.set("new", new NewFunction());
		env.set("super", ClassMethod.superTable());
		if (!env.get("package").isnil()) env.get("package").get("loaded").set(PACKAGE_NAME, lib);
		return lib;
	}

	/** {@code class.get(name)}: the class registered under exactly that name, or nil. */
	final class GetFunction extends OneArgFunction {
		@Override
		public LuaValue call(LuaValue className) {
			ClassLuaTable classTable = classManager.getClassTable(className.checkjstring());
			return classTable != null ? classTable : NIL;
		}
	}

	/**
	 * {@code class.register(class [, base])}: Lua's side of {@link LuaClassManager#registerClass(LuaValue, LuaValue)}.
	 * {@code class} is the class's table or just its name (an empty class); {@code base} a class, a class name or nil.
	 */
	final class RegisterFunction extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue classDef = args.arg1();
			if (!classDef.istable() && (!classDef.isstring() || classDef.isnumber())) {
				return argerror(1, "table or class name expected, got " + classDef.typename());
			}
			LuaValue base = args.arg(2);
			if (!base.isnil() && !(base instanceof ClassLuaTable) && (!base.isstring() || base.isnumber())) {
				return argerror(2, "class or class name expected, got " + base.typename());
			}
			if (args.narg() > 2) {
				return argerror(3, "class.register takes a class and, optionally, its base");
			}
			return classManager.registerClass(classDef, base);
		}
	}

	/**
	 * {@code new(class, ...)} or {@code new(name, ...)}: a new instance of the class, or of the class registered by
	 * that name; the other arguments go to its constructor.
	 */
	final class NewFunction extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {// Returns an InstanceLuaTable
			LuaValue table = args.arg1();
			if (table.isstring() && !table.isnumber()) {
				ClassLuaTable named = classManager.getClassTable(table.tojstring());
				if (named == null) {
					return argerror(1, "no class registered as '" + table.tojstring() + "'");
				}
				return named.createInstance(args.subargs(2));
			}
			if (!(table instanceof ClassLuaTable)) {
				return argerror(1, "class or class name expected, got " + table.typename());
			}
			return ((ClassLuaTable) table).createInstance(args.subargs(2));
		}
	}

	/** {@code isclass(value)}: whether the value is a class (any value; false for anything else). */
	final class IsClassFunction extends OneArgFunction {
		@Override
		public LuaValue call(LuaValue arg) {// Returns a LuaBoolean
			return valueOf(arg instanceof ClassLuaTable);
		}
	}

	/** {@code isobj(value)}: whether the value is an instance of a class (any value; false for anything else). */
	final class IsObjFunction extends OneArgFunction {
		@Override
		public LuaValue call(LuaValue arg) {// Returns a LuaBoolean
			return valueOf(arg instanceof InstanceLuaTable);
		}
	}

	/**
	 * An argument error in Lua's own wording: {@code bad argument #2 to 'instanceof' (class expected, got table)}. A
	 * class or an instance given where it doesn't belong is called that, not just "table"; an argument left out is
	 * "no value", as Lua says.
	 */
	static LuaError badArgument(Varargs args, int index, String function, String expected) {
		LuaValue got = args.arg(index);
		String kind = index > args.narg() ? "no value" : got instanceof ClassLuaTable ? "class"
				: got instanceof InstanceLuaTable ? "instance" : got.typename();
		return new LuaError("bad argument #" + index + " to '" + function + "' (" + expected + " expected, got " + kind + ")");
	}

	/** A class argument: a class, or the name of a registered one. */
	ClassLuaTable classArgument(Varargs args, int index, String function) {
		LuaValue value = args.arg(index);
		if (value instanceof ClassLuaTable) {
			return (ClassLuaTable) value;
		}
		if (value.isstring() && !value.isnumber()) {
			ClassLuaTable named = classManager.getClassTable(value.tojstring());
			if (named == null) {
				throw new LuaError("bad argument #" + index + " to '" + function + "' (no class registered as '"
						+ value.tojstring() + "')");
			}
			return named;
		}
		throw badArgument(args, index, function, "class or class name");
	}

	/** {@code class.list([prefix])}: the registered class names (under a dotted prefix), sorted, as an array. */
	final class ListFunction extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue prefix = args.arg1();
			if (!prefix.isnil() && (!prefix.isstring() || prefix.isnumber())) {
				throw badArgument(args, 1, "list", "string");
			}
			LuaTable out = new LuaTable();
			for (String name : classManager.getClassNames(prefix.isnil() ? null : prefix.tojstring())) {
				out.insert(0, valueOf(name));
			}
			return out;
		}
	}

	/** {@code class.subclasses(class [, all])}: a class's subclasses as an array; every class below it with {@code all}. */
	final class SubclassesFunction extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			ClassLuaTable classTable = classArgument(args, 1, "subclasses");
			LuaTable out = new LuaTable();
			for (ClassLuaTable subclass : classManager.getSubclasses(classTable, args.arg(2).toboolean())) {
				out.insert(0, subclass);
			}
			return out;
		}
	}

	/**
	 * {@code class.extends(a, b [, exact])}: whether class {@code a} is {@code b} or extends it (only whether it's
	 * {@code b} with {@code exact}). Both are classes or class names.
	 */
	final class ExtendsFunction extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			ClassLuaTable a = classArgument(args, 1, "extends");
			ClassLuaTable b = classArgument(args, 2, "extends");
			return valueOf(classManager.isSubclass(a, b, args.arg(3).toboolean()));
		}
	}

	/**
	 * {@code instanceof(instance, class [, exact])}: whether the instance is of the class or one of its subclasses
	 * (only of that very class with {@code exact}). The class may be given by name.
	 */
	final class InstanceofFunction extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue instance = args.arg1();
			if (!(instance instanceof InstanceLuaTable)) {
				throw badArgument(args, 1, "instanceof", "instance");
			}
			ClassLuaTable classTable = classArgument(args, 2, "instanceof");
			return valueOf(classManager.isInstance((InstanceLuaTable) instance, classTable, args.arg(3).toboolean()));
		}
	}
}
