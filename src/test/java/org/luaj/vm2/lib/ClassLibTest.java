package org.luaj.vm2.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.ClassLuaTable;
import org.luaj.vm2.InstanceLuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** The class library's documented behaviour (README, "Reference: intended behaviour"), but super (SuperTest). */
class ClassLibTest extends ClassLibTestBase {

	@Test
	void installsItsGlobals() {
		Varargs v = run("return class, package.loaded.class, new, super, isclass, isobj, instanceof,"
				+ " class.register, class.get, class.list, class.subclasses, class.extends");
		for (int i = 1; i <= 12; i++) {
			assertFalse(v.arg(i).isnil(), "global " + i);
		}
		assertTrue(v.arg(1) == v.arg(2));
	}

	@Nested
	class Registering {

		@Test
		void returnsANewClassTable() {
			Varargs v = run("local t = { __classname = 'demo.point' } local C = class.register(t) return C, t, isclass(C)");
			assertTrue(v.arg1() instanceof ClassLuaTable);
			assertTrue(v.arg1() != v.arg(2));
			assertTrue(v.arg(3).toboolean());
		}

		@Test
		void keepsTheSystemFields() {
			Varargs v = run("local A = class.register({ __classname = 'game.entity' })"
					+ " local B = class.register({ __classname = 'game.weapon.gun', __basename = 'game.entity' })"
					+ " return B.__classname, B.__shortname, B.__basename, B.__class == B, B.__base == A,"
					+ " A.__shortname, A.__basename, A.__base");
			assertEquals("game.weapon.gun", v.arg(1).tojstring());
			assertEquals("gun", v.arg(2).tojstring());
			assertEquals("game.entity", v.arg(3).tojstring());
			assertTrue(v.arg(4).toboolean());
			assertTrue(v.arg(5).toboolean());
			assertEquals("entity", v.arg(6).tojstring());
			assertEquals("", v.arg(7).tojstring());
			assertTrue(v.arg(8).isnil());
		}

		@Test
		void namesAreCaseSensitive() {
			Varargs v = run("local a = class.register({ __classname = 'Dog' }) local b = class.register({ __classname = 'dog' })"
					+ " return a ~= b, class.get('Dog') == a, class.get('dog') == b, class.get('DOG')");
			assertTrue(v.arg(1).toboolean());
			assertTrue(v.arg(2).toboolean());
			assertTrue(v.arg(3).toboolean());
			assertTrue(v.arg(4).isnil());
		}

		@Test
		void rejectsInvalidNames() {
			assertTrue(error("class.register({})").contains("must be a string, got nil"));
			assertTrue(error("class.register({ __classname = 5 })").contains("must be a string, got number"));
			assertTrue(error("class.register({ __classname = '' })").contains("must not be empty"));
			assertTrue(error("class.register({ __classname = '.a' })").contains("must not start with '.'"));
			assertTrue(error("class.register({ __classname = 'a.' })").contains("must not end with '.'"));
			assertTrue(error("class.register({ __classname = 'a..b' })").contains("must not have two '.' in a row"));
		}

		@Test
		void rejectsANameRegisteredTwice() {
			run("class.register({ __classname = 'dup' })");
			assertTrue(error("class.register({ __classname = 'dup' })").contains("'dup' is already registered"));
		}

		@Test
		void takesJustANameForAnEmptyClass() {
			Varargs v = run("local C = class.register('game.empty') return C.__classname, C.__shortname, C.__base, isobj(C()), type(C.__)");
			assertEquals("game.empty", v.arg(1).tojstring());
			assertEquals("empty", v.arg(2).tojstring());
			assertTrue(v.arg(3).isnil());
			assertTrue(v.arg(4).toboolean());
			assertEquals("function", v.arg(5).tojstring());
		}

		@Test
		void takesTheBaseAsASecondArgumentByNameOrClass() {
			Varargs v = run("local A = class.register('a') local B = class.register('b', 'a')"
					+ " local C = class.register({ __classname = 'c' }, B) return B.__base == A, C.__base == B, C.__basename");
			assertTrue(v.arg(1).toboolean());
			assertTrue(v.arg(2).toboolean());
			assertEquals("b", v.arg(3).tojstring());
		}

		@Test
		void prefersTheBaseArgumentToTheTablesBasename() {
			assertEquals("b", eval("class.register('a') class.register('b')"
					+ " return class.register({ __classname = 'c', __basename = 'a' }, 'b').__basename").tojstring());
		}

		@Test
		void fallsBackToTheTablesBasenameWhenTheBaseIsNil() {
			Varargs v = run("class.register('a') local B = class.register({ __classname = 'b', __basename = 'a' }, nil)"
					+ " local C = class.register('c', nil) return B.__basename, C.__base");
			assertEquals("a", v.arg(1).tojstring());
			assertTrue(v.arg(2).isnil());
		}

		@Test
		void rejectsWrongArguments() {
			run("class.register('base')");
			assertTrue(error("class.register()").contains("bad argument #1 (table or class name expected, got nil)"));
			assertTrue(error("class.register(5)").contains("bad argument #1 (table or class name expected, got number)"));
			assertTrue(error("class.register('x', 5)").contains("bad argument #2 (class or class name expected, got number)"));
			assertTrue(error("class.register('x', {})").contains("bad argument #2 (class or class name expected, got table)"));
			assertTrue(error("class.register('x', 'nope')").contains("base class 'nope' isn't registered"));
			assertTrue(error("class.register('x', 'base', 'extra')").contains("bad argument #3"));
			assertTrue(error("class.register('a..b')").contains("must not have two '.' in a row"));
		}

		@Test
		void rejectsABaseFromAnotherClassLibrary() {
			LuaValue foreign = globals(false).load("return class.register('shared')").call();
			globals.set("foreign", foreign);
			assertTrue(error("class.register('x', foreign)").contains("isn't registered with this class library"));
		}

		@Test
		void requiresARegisteredBase() {
			assertTrue(error("class.register({ __classname = 'x', __basename = 'nope' })").contains("base class 'nope' isn't registered"));
			assertTrue(error("class.register({ __classname = 'y', __basename = 5 })").contains("__basename must be a string"));
		}

		@Test
		void leavesNothingBehindWhenItFails() {
			error("class.register({ __classname = 'x', __basename = 'nope' })");
			error("class.register('y', 'nope')");
			error("class.register({ __classname = 'z', __ = 5 })");
			Varargs v = run("return class.get('x'), class.get('y'), class.get('z')");
			assertTrue(v.arg(1).isnil() && v.arg(2).isnil() && v.arg(3).isnil());
		}
	}

	@Nested
	class Inheritance {

		@Test
		void copiesTheBasesMembersAndLetsTheClassOverrideThem() {
			Varargs v = run("local A = class.register({ __classname = 'a', f = function() return 'A.f' end, g = function() return 'A.g' end })"
					+ " local B = class.register({ __classname = 'b', __basename = 'a', g = function() return 'B.g' end })"
					+ " return B.f(), B.g(), A.g()");
			assertEquals("A.f", v.arg(1).tojstring());
			assertEquals("B.g", v.arg(2).tojstring());
			assertEquals("A.g", v.arg(3).tojstring());
		}

		@Test
		void keepsSubclassesUpToDateWithTheirBase() {
			LuaValue later = eval("local A = class.register({ __classname = 'a' })"
					+ " local B = class.register({ __classname = 'b', __basename = 'a' })"
					+ " A.later = function() return 'later' end return B.later()");
			assertEquals("later", later.tojstring());
		}

		@Test
		void allowsAnyNumberOfRootClasses() {
			Varargs v = run("local A = class.register({ __classname = 'a' }) local X = class.register({ __classname = 'x' })"
					+ " local B = class.register({ __classname = 'b', __basename = 'a' }) return A.__base, X.__base, B.__base == A");
			assertTrue(v.arg(1).isnil());
			assertTrue(v.arg(2).isnil());
			assertTrue(v.arg(3).toboolean());
		}
	}

	@Nested
	class InstanceData {

		private static final String CLASS = "local C = class.register({ __classname = 'c', kind = 'shared',"
				+ " speak = function(self) return 'class' end }) local a, b = C(), C()";

		@Test
		void aFieldSetOnAnInstanceMasksTheClassesForThatInstanceOnly() {
			Varargs v = run(CLASS + " a.kind = 'mine' return a.kind, rawget(a, 'kind'), b.kind, C.kind");
			assertEquals("mine", v.arg(1).tojstring());
			assertEquals("mine", v.arg(2).tojstring());
			assertEquals("shared", v.arg(3).tojstring());
			assertEquals("shared", v.arg(4).tojstring());
		}

		@Test
		void settingItToNilMasksItWithNil() {
			Varargs v = run(CLASS + " a.kind = 'mine' a.kind = nil b.kind = nil"
					+ " return a.kind, rawget(a, 'kind'), b.kind, C.kind, C().kind");
			assertTrue(v.arg(1).isnil());
			assertTrue(v.arg(2).isnil());
			assertTrue(v.arg(3).isnil());                   // never set before: nil masks it too
			assertEquals("shared", v.arg(4).tojstring());
			assertEquals("shared", v.arg(5).tojstring());
		}

		@Test
		void aMethodMaskedWithNilIsGoneForThatInstance() {
			Varargs v = run(CLASS + " a.speak = nil return a.speak, b:speak(), pcall(function() return a:speak() end)");
			assertTrue(v.arg(1).isnil());
			assertEquals("class", v.arg(2).tojstring());
			assertFalse(v.arg(3).toboolean());
		}

		@Test
		void assigningTheClassesValueTakesItAgainAsTheInstancesOwn() {
			Varargs v = run(CLASS + " a.kind = nil a.kind = C.kind C.kind = 'changed' return a.kind, b.kind");
			assertEquals("shared", v.arg(1).tojstring());    // a copy of the class's value at the time
			assertEquals("changed", v.arg(2).tojstring());
		}

		@Test
		void pairsAndNextSkipMaskedNils() {
			LuaValue keys = eval(CLASS + " a.kind = nil a.own = 1 a.other = 2 local k = {}"
					+ " for key, value in pairs(a) do k[#k + 1] = key .. '=' .. value end table.sort(k) return table.concat(k, ' ')");
			assertEquals("other=2 own=1", keys.tojstring());
		}

		@Test
		void nilForAKeyTheClassDoesntHaveRemovesIt() {
			Varargs v = run(CLASS + " a.extra = 1 a.extra = nil local n = 0 for _ in pairs(a) do n = n + 1 end return a.extra, n");
			assertTrue(v.arg(1).isnil());
			assertEquals(0, v.arg(2).toint());
		}

		@ParameterizedTest(name = "luajc={0}")
		@ValueSource(booleans = { false, true })
		void maskingWorksTheSameCompiled(boolean luajc) {
			globals = globals(luajc);
			assertEquals("nil nil white mine white", eval(CLASS.replace("'shared'", "'white'")
					+ " a.kind = nil local first = tostring(a.kind) .. ' ' .. tostring(rawget(a, 'kind'))"
					+ " b.kind = 'mine' local second = b.kind a.kind = C.kind"
					+ " return first .. ' ' .. C.kind .. ' ' .. second .. ' ' .. a.kind").tojstring());
		}

		@Test
		void aMaskedNilCanBeOverwritten() {
			assertEquals("green", eval(CLASS + " a.kind = nil a.kind = 'green' return a.kind").tojstring());
		}

		@Test
		void aMaskedMemberKeepsTheInstancesValueWhenTheClassChanges() {
			Varargs v = run(CLASS + " a.kind = 'mine' C.kind = 'changed' return a.kind, b.kind");
			assertEquals("mine", v.arg(1).tojstring());
			assertEquals("changed", v.arg(2).tojstring());
		}

		@Test
		void systemFieldsCantBeSetOnAnInstance() {
			for (String field : new String[] { "__classname", "__shortname", "__basename", "__class", "__base" }) {
				String expected = "instance of 'c" + field + "': " + field + " can't be set";
				run("i" + field + " = class.register('c" + field + "')()");
				assertTrue(error("i" + field + "." + field + " = 'x'").contains(expected), field);
				assertTrue(error("rawset(i" + field + ", '" + field + "', 'x')").contains(expected), field);
				assertTrue(error("i" + field + "." + field + " = nil").contains(expected), field);
			}
		}

		@Test
		void aConstructorCantSetOneEither() {
			assertTrue(error("local D = class.register({ __classname = 'd', __ = function(self) self.__class = 1 end }) D()")
					.contains("instance of 'd': __class can't be set"));
		}

		@Test
		void otherDoubleUnderscoreKeysAreOrdinaryOnAnInstance() {
			Varargs v = run(CLASS + " a.__ = 'x' a.__tostring = 'y' return a.__, a.__tostring, isobj(C()), tostring(a):sub(1, 5)");
			assertEquals("x", v.arg(1).tojstring());
			assertEquals("y", v.arg(2).tojstring());
			assertTrue(v.arg(3).toboolean());          // the class's constructor still makes instances
			assertEquals("table", v.arg(4).tojstring());  // metamethods come from the class, not the instance
		}

		@Test
		void aMethodCanBeOverriddenOnOneInstance() {
			Varargs v = run(CLASS + " a.speak = function(self) return 'own' end return a:speak(), b:speak()");
			assertEquals("own", v.arg(1).tojstring());
			assertEquals("class", v.arg(2).tojstring());
		}
	}

	@Nested
	class Mutability {

		/** a <- b <- c <- d; only a defines f. */
		private static final String CHAIN = "local A = class.register({ __classname = 'a', f = function() return 'A' end })"
				+ " local B = class.register({ __classname = 'b', __basename = 'a' })"
				+ " local C = class.register({ __classname = 'c', __basename = 'b' })"
				+ " local D = class.register({ __classname = 'd', __basename = 'c' })";

		@Test
		void aChangeReachesEverySubclassThatDoesntDefineTheMember() {
			Varargs v = run(CHAIN + " C.f = function() return 'C' end A.f = function() return 'A2' end"
					+ " return B.f(), C.f(), D.f(), new(D).f()");
			assertEquals("A2", v.arg(1).tojstring());
			assertEquals("C", v.arg(2).tojstring());   // defines f itself: the base's change doesn't reach it
			assertEquals("C", v.arg(3).tojstring());   // inherits C's
			assertEquals("C", v.arg(4).tojstring());
		}

		@Test
		void removingAnOverrideInheritsTheBasesAgain() {
			Varargs v = run(CHAIN + " C.f = function() return 'C' end local before = D.f() C.f = nil"
					+ " return before, C.f(), D.f()");
			assertEquals("C", v.arg(1).tojstring());
			assertEquals("A", v.arg(2).tojstring());
			assertEquals("A", v.arg(3).tojstring());
		}

		@Test
		void removingFromTheBaseRemovesItWhereItWasInherited() {
			Varargs v = run(CHAIN + " C.f = function() return 'C' end A.f = nil return B.f, C.f(), D.f()");
			assertTrue(v.arg(1).isnil());
			assertEquals("C", v.arg(2).tojstring());
			assertEquals("C", v.arg(3).tojstring());
		}

		@Test
		void aClassRegisteredLaterTakesTheCurrentMembers() {
			LuaValue f = eval(CHAIN + " B.f = function() return 'B' end"
					+ " local E = class.register({ __classname = 'e', __basename = 'd' }) return E.f()");
			assertEquals("B", f.tojstring());
		}

		@Test
		void constructorsFollowLikeMethods() {
			Varargs v = run("local A = class.register({ __classname = 'a' })"
					+ " local B = class.register({ __classname = 'b', __basename = 'a' })"
					+ " A.__ = function(self) self.made = 'A' end local first = new(B).made"
					+ " A.__ = nil return first, new(B).made, type(B.__)");
			assertEquals("A", v.arg(1).tojstring());
			assertTrue(v.arg(2).isnil());
			assertEquals("function", v.arg(3).tojstring());   // back to its own default
		}

		@Test
		void rawsetIsAChangeToo() {
			assertEquals("raw", eval(CHAIN + " rawset(A, 'f', function() return 'raw' end) return D.f()").tojstring());
		}

		@Test
		void systemFieldsCantBeSet() {
			for (String field : new String[] { "__classname", "__shortname", "__basename", "__class", "__base" }) {
				assertTrue(error("local C = class.register({ __classname = 'c" + field + "' }) C." + field + " = 1")
						.contains(field + " can't be set"), field);
			}
		}

		@Test
		void instancesAreUnaffected() {
			Varargs v = run(CHAIN + " local d = new(D) d.f = function() return 'own' end return d.f(), D.f()");
			assertEquals("own", v.arg(1).tojstring());
			assertEquals("A", v.arg(2).tojstring());
		}
	}

	@Nested
	class Instances {

		@Test
		void comeFromCallingTheClassOrNewByClassOrName() {
			Varargs v = run("local C = class.register({ __classname = 'demo.c' })"
					+ " local a, b, c = C(), new(C), new('demo.c')"
					+ " return a, getmetatable(a) == C, getmetatable(b) == C, getmetatable(c) == C, a ~= b");
			assertTrue(v.arg1() instanceof InstanceLuaTable);
			for (int i = 2; i <= 5; i++) {
				assertTrue(v.arg(i).toboolean(), "result " + i);
			}
		}

		@Test
		void callingTheClassPassesAnyNumberOfArguments() {
			assertEquals(",1,1-2,1-2-3,1-2-3-4-5", eval("local C = class.register({ __classname = 'c',"
					+ " __ = function(self, ...) self.args = table.concat({ ... }, '-') end })"
					+ " return table.concat({ C().args, C(1).args, C(1, 2).args, C(1, 2, 3).args, C(1, 2, 3, 4, 5).args }, ',')").tojstring());
		}

		@Test
		void callingTheClassWorksThroughPcallAndTailCalls() {
			Varargs v = run("local C = class.register({ __classname = 'c', __ = function(self, x) self.x = x end })"
					+ " local function make(x) return C(x) end local ok, viaPcall = pcall(C, 1)"
					+ " return viaPcall.x, make(2).x, getmetatable(C)");
			assertEquals(1, v.arg(1).toint());
			assertEquals(2, v.arg(2).toint());
			assertTrue(v.arg(3).isnil());   // the class needs no metatable to be called
		}

		@Test
		void aCallMemberMakesInstancesCallableNotTheClass() {
			Varargs v = run("local C = class.register({ __classname = 'c', __call = function(self, x) return 'called ' .. x end })"
					+ " local i = C() return isobj(i), i(7)");
			assertTrue(v.arg(1).toboolean());
			assertEquals("called 7", v.arg(2).tojstring());
		}

		@Test
		void newAndConstrAreOrdinaryKeys() {
			Varargs v = run("local A = class.register({ __classname = 'a', __new = 'n', __constr = 'c' })"
					+ " local B = class.register('b', 'a') B.__new = 'set' return A.__new, A.__constr, B.__constr, B.__new, isobj(B())");
			assertEquals("n", v.arg(1).tojstring());
			assertEquals("c", v.arg(2).tojstring());
			assertEquals("c", v.arg(3).tojstring());
			assertEquals("set", v.arg(4).tojstring());
			assertTrue(v.arg(5).toboolean());
		}

		@Test
		void getTheMetamethodsTheirClassDefines() {
			Varargs v = run("local V = class.register('vec') function V:__(x, y) self.x, self.y = x, y end"
					+ " V.__tostring = function(s) return '(' .. s.x .. ', ' .. s.y .. ')' end"
					+ " V.__add = function(a, b) return V(a.x + b.x, a.y + b.y) end"
					+ " V.__eq = function(a, b) return a.x == b.x and a.y == b.y end"
					+ " local V3 = class.register('vec3', V)"
					+ " return tostring(V(1, 2) + V(3, 4)), V(1, 2) == V(1, 2), tostring(V3(5, 6))");
			assertEquals("(4, 6)", v.arg(1).tojstring());
			assertTrue(v.arg(2).toboolean());
			assertEquals("(5, 6)", v.arg(3).tojstring());   // inherited
		}

		@Test
		void haveTheirOwnFieldsAndFindTheClassesThroughIt() {
			Varargs v = run("local C = class.register({ __classname = 'c', kind = 'shared', who = function(self) return self.name end })"
					+ " local a, b = new(C), new(C) a.name = 'a' b.name = 'b'"
					+ " return a:who(), b:who(), a.kind, a.__classname, rawget(a, 'kind')");
			assertEquals("a", v.arg(1).tojstring());
			assertEquals("b", v.arg(2).tojstring());
			assertEquals("shared", v.arg(3).tojstring());
			assertEquals("c", v.arg(4).tojstring());
			assertTrue(v.arg(5).isnil());
		}

		@Test
		void isclassAndIsobjAnswerForAnyValue() {
			Varargs v = run("local C = class.register({ __classname = 'c' }) local i = new(C)"
					+ " return isclass(C), isobj(i), isclass(i), isobj(C), isclass({}), isobj('x'), isclass(nil), isobj(5)");
			assertTrue(v.arg(1).toboolean());
			assertTrue(v.arg(2).toboolean());
			for (int i = 3; i <= 8; i++) {
				assertFalse(v.arg(i).toboolean(), "result " + i);
			}
		}

		@Test
		void newRejectsWhatIsntAClass() {
			assertTrue(error("new({})").contains("class or class name expected, got table"));
			assertTrue(error("new(5)").contains("class or class name expected, got number"));
			assertTrue(error("new('nope')").contains("no class registered as 'nope'"));
		}
	}

	@Nested
	class Queries {

		private static final String TREE = "local W = class.register('game.weapon')"
				+ " local G = class.register('game.weapon.gun', W) local P = class.register('game.weapon.pistol', G)"
				+ " local K = class.register('game.weapon.knife', W) class.register('game.weaponry') class.register('other')";

		@Test
		void listsClassNamesSortedAndUnderADottedPrefix() {
			Varargs v = run(TREE + " return table.concat(class.list(), ' '), table.concat(class.list('game.weapon'), ' '), #class.list('nope')");
			assertEquals("game.weapon game.weapon.gun game.weapon.knife game.weapon.pistol game.weaponry other", v.arg(1).tojstring());
			assertEquals("game.weapon game.weapon.gun game.weapon.knife game.weapon.pistol", v.arg(2).tojstring());
			assertEquals(0, v.arg(3).toint());
		}

		@Test
		void listsSubclassesDirectOrAll() {
			Varargs v = run(TREE + " local function names(t) local o = {} for i, c in ipairs(t) do o[i] = c.__classname end return table.concat(o, ' ') end"
					+ " return names(class.subclasses(W)), names(class.subclasses('game.weapon', true)), #class.subclasses(P)");
			assertEquals("game.weapon.gun game.weapon.knife", v.arg(1).tojstring());
			assertEquals("game.weapon.gun game.weapon.pistol game.weapon.knife", v.arg(2).tojstring());
			assertEquals(0, v.arg(3).toint());
		}

		@Test
		void extendsTellsWhetherAClassIsOrExtendsAnother() {
			Varargs v = run(TREE + " return class.extends(P, W), class.extends(P, 'game.weapon.gun'), class.extends(W, P),"
					+ " class.extends(P, P), class.extends(P, W, true), class.extends(P, P, true), class.extends(K, G)");
			boolean[] expected = { true, true, false, true, false, true, false };
			for (int i = 0; i < expected.length; i++) {
				assertEquals(expected[i], v.arg(i + 1).toboolean(), "result " + (i + 1));
			}
		}

		@Test
		void instanceofTellsWhetherAnInstanceIsOfAClassOrASubclass() {
			Varargs v = run(TREE + " local p = P() return instanceof(p, W), instanceof(p, 'game.weapon.pistol'), instanceof(p, K),"
					+ " instanceof(p, W, true), instanceof(p, P, true)");
			boolean[] expected = { true, true, false, false, true };
			for (int i = 0; i < expected.length; i++) {
				assertEquals(expected[i], v.arg(i + 1).toboolean(), "result " + (i + 1));
			}
		}

		@Test
		void reportWrongArgumentsInLuasWording() {
			run(TREE + " p = P()");
			assertTrue(error("instanceof({}, W)").contains("bad argument #1 to 'instanceof' (instance expected, got table)"));
			assertTrue(error("instanceof(class.get('game.weapon'), 'game.weapon')").contains("(instance expected, got class)"));
			assertTrue(error("instanceof()").contains("(instance expected, got no value)"));
			assertTrue(error("instanceof(nil, 'game.weapon')").contains("(instance expected, got nil)"));
			assertTrue(error("instanceof(p, p)").contains("bad argument #2 to 'instanceof' (class or class name expected, got instance)"));
			assertTrue(error("instanceof(p, 'game.wepon')").contains("(no class registered as 'game.wepon')"));
			assertTrue(error("class.extends(p, 'game.weapon')").contains("bad argument #1 to 'extends' (class or class name expected, got instance)"));
			assertTrue(error("class.subclasses('nope')").contains("bad argument #1 to 'subclasses' (no class registered as 'nope')"));
			assertTrue(error("class.list(5)").contains("bad argument #1 to 'list' (string expected, got number)"));
		}
	}

	@Nested
	class Constructors {

		@Test
		void getTheArgumentsTheInstanceIsMadeWith() {
			Varargs v = run("local C = class.register({ __classname = 'c', __ = function(self, a, b) self.a, self.b = a, b end })"
					+ " local x, y, z = new(C, 1, 2), new('c', 3, 4), C(5, 6) return x.a, x.b, y.b, z.a");
			assertEquals(1, v.arg(1).toint());
			assertEquals(2, v.arg(2).toint());
			assertEquals(4, v.arg(3).toint());
			assertEquals(5, v.arg(4).toint());
		}

		@Test
		void areCopiedIntoSubclassesLikeMethods() {
			LuaValue a = eval("class.register({ __classname = 'a', __ = function(self, x) self.x = x end })"
					+ " local B = class.register({ __classname = 'b', __basename = 'a' }) return new(B, 7).x");
			assertEquals(7, a.toint());
		}

		@Test
		void chainWithSuper() {
			LuaValue log = eval("local log = {}"
					+ " class.register({ __classname = 'a', __ = function(self) log[#log + 1] = 'a' end })"
					+ " class.register({ __classname = 'b', __basename = 'a' })"
					+ " class.register({ __classname = 'c', __basename = 'b', __ = function(self) super.__(self) log[#log + 1] = 'c' end })"
					+ " new('c') return table.concat(log, '>')");
			assertEquals("a>c", log.tojstring());
		}

		@Test
		void defaultToOneThatDoesNothing() {
			Varargs v = run("local A = class.register({ __classname = 'a' })"
					+ " local B = class.register({ __classname = 'b', __basename = 'a', __ = function(self) super.__(self) self.ok = true end })"
					+ " return type(A.__), new(B).ok");
			assertEquals("function", v.arg(1).tojstring());
			assertTrue(v.arg(2).toboolean());
		}

		@Test
		void canBeDefinedWithTheColonSyntaxOnAClassMadeByName() {
			assertEquals("rifle 30", eval("local Gun = class.register('gun') function Gun:__(name, ammo) self.name, self.ammo = name, ammo end"
					+ " local g = Gun('rifle', 30) return g.name .. ' ' .. g.ammo").tojstring());
		}

		@Test
		void mustBeFunctions() {
			assertTrue(error("class.register({ __classname = 'c', __ = 5 })").contains("__ must be a function, got number"));
			assertTrue(eval("return class.get('c')").isnil());
		}

		@Test
		void raiseTheirErrorsFromNew() {
			assertTrue(error("new(class.register({ __classname = 'c', __ = function() error('boom') end }))").contains("boom"));
		}
	}
}
