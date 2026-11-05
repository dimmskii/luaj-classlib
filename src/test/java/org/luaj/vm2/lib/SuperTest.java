package org.luaj.vm2.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** super, run interpreted and compiled with LuaJC: the README says it works the same in both. */
class SuperTest extends ClassLibTestBase {

	/** Five classes, each extending the one before; 1st, 3rd and 5th implement hello, 3rd and 5th call super. */
	private static final String CHAIN = "local log = {}"
			+ " class.register({ __classname = 'c1', hello = function(self) log[#log + 1] = 'c1' end })"
			+ " class.register({ __classname = 'c2', __basename = 'c1' })"
			+ " class.register({ __classname = 'c3', __basename = 'c2', hello = function(self) log[#log + 1] = 'c3' super.hello(self) end })"
			+ " class.register({ __classname = 'c4', __basename = 'c3' })"
			+ " class.register({ __classname = 'c5', __basename = 'c4', hello = function(self) log[#log + 1] = 'c5' super.hello(self) end })";

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void goesOneClassUpAtEveryLevelAndSkipsClassesWithoutTheMethod(boolean luajc) {
		globals = globals(luajc);
		assertEquals("c5 c3 c1", eval(CHAIN + " new('c5'):hello() return table.concat(log, ' ')").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void worksThroughTailCalls(boolean luajc) {
		globals = globals(luajc);
		assertEquals("A", eval("class.register({ __classname = 'a', f = function(self) return 'A' end })"
				+ " class.register({ __classname = 'b', __basename = 'a', f = function(self) return super.f(self) end })"
				+ " return new('b'):f()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void staysRightAfterAnErrorInTheBase(boolean luajc) {
		globals = globals(luajc);
		assertEquals("caught B>A", eval("class.register({ __classname = 'a', f = function(self) error('no') end, g = function(self) return 'A' end })"
				+ " class.register({ __classname = 'b', __basename = 'a', g = function(self) return 'B>' .. super.g(self) end,"
				+ "   f = function(self) local ok = pcall(super.f, self) return (ok and 'not ' or '') .. 'caught ' .. self:g() end })"
				+ " return new('b'):f()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void survivesAYieldInTheMiddleOfAMethod(boolean luajc) {
		globals = globals(luajc);
		assertEquals("B>A", eval("class.register({ __classname = 'a', f = function(self) coroutine.yield() return 'A' end, g = function(self) return 'g' end })"
				+ " class.register({ __classname = 'b', __basename = 'a', f = function(self) return 'B>' .. super.f(self) end })"
				+ " local b = new('b') local co = coroutine.create(function() return b:f() end)"
				+ " coroutine.resume(co) b:g() local _, r = coroutine.resume(co) return r").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void isAnErrorOutsideAMethodOrInARootClass(boolean luajc) {
		globals = globals(luajc);
		assertTrue(error("return super.f").contains("super: not inside a class method"));
		assertTrue(error("class.register({ __classname = 'r', f = function(self) return super.f(self) end }) return new('r'):f()")
				.contains("super: class 'r' has no base class"));
	}

	/** a <- b <- c; a and c define f, c's calls super; b defines nothing at first. */
	private static final String GAP = "class.register({ __classname = 'a', f = function(self) return 'A' end })"
			+ " local B = class.register({ __classname = 'b', __basename = 'a' })"
			+ " local C = class.register({ __classname = 'c', __basename = 'b', f = function(self) return 'C>' .. super.f(self) end })";

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void aMethodAddedToAMiddleClassLaterEntersTheChain(boolean luajc) {
		globals = globals(luajc);
		assertEquals("C>A C>B>A", eval(GAP + " local before = new(C):f()"
				+ " B.f = function(self) return 'B>' .. super.f(self) end"
				+ " return before .. ' ' .. new(C):f()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void anOverrideRemovedFromAMiddleClassLeavesTheChain(boolean luajc) {
		globals = globals(luajc);
		assertEquals("C>B>A C>A", eval(GAP + " B.f = function(self) return 'B>' .. super.f(self) end"
				+ " local before = new(C):f() B.f = nil return before .. ' ' .. new(C):f()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void aChangedBaseMethodIsWhatSuperCalls(boolean luajc) {
		globals = globals(luajc);
		assertEquals("C>A2", eval(GAP + " class.get('a').f = function(self) return 'A2' end return new(C):f()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void aMethodAddedAfterRegisteringCanUseSuper(boolean luajc) {
		globals = globals(luajc);
		assertEquals("B>A", eval(GAP + " function B:g() return 'B>' .. super.f(self) end return new(C):g()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void aConstructorAddedToAMiddleClassEntersTheChain(boolean luajc) {
		globals = globals(luajc);
		assertEquals("a>b>c", eval("local log = {}"
				+ " class.register({ __classname = 'a', __ = function(self) log[#log + 1] = 'a' end })"
				+ " local B = class.register({ __classname = 'b', __basename = 'a' })"
				+ " class.register({ __classname = 'c', __basename = 'b', __ = function(self) super.__(self) log[#log + 1] = 'c' end })"
				+ " B.__ = function(self) super.__(self) log[#log + 1] = 'b' end"
				+ " new('c') return table.concat(log, '>')").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void constructorsChainWhenTheClassIsCalled(boolean luajc) {
		globals = globals(luajc);
		assertEquals("base>gun sidearm 12", eval("local Base = class.register('base') function Base:__(name) self.log = 'base' self.name = name end"
				+ " local Gun = class.register('gun', Base) function Gun:__(name, ammo) super.__(self, name) self.log = self.log .. '>gun' self.ammo = ammo end"
				+ " local Pistol = class.register('pistol', 'gun')"
				+ " local p = Pistol('sidearm', 12) return p.log .. ' ' .. p.name .. ' ' .. p.ammo").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void methodsAddedToAClassMadeByNameCanUseSuper(boolean luajc) {
		globals = globals(luajc);
		assertEquals("b>a", eval("local A = class.register('a') function A:hi() return 'a' end"
				+ " local B = class.register('b', A) function B:hi() return 'b>' .. super.hi(self) end return B():hi()").tojstring());
	}

	@ParameterizedTest(name = "luajc={0}")
	@ValueSource(booleans = { false, true })
	void aMethodKeptBeforeOverridingItOnAnInstanceStillCallsSuper(boolean luajc) {
		globals = globals(luajc);
		assertEquals("own>B>A talks: own>B>A", eval("local A = class.register('a') function A:speak() return 'A' end"
				+ " local B = class.register('b', A) function B:speak() return 'B>' .. super.speak(self) end"
				+ " function B:talk() return 'talks: ' .. self:speak() end"
				+ " local ent = B() ent.old_speak = ent.speak ent.speak = function(self) return 'own>' .. self:old_speak() end"
				+ " return ent:speak() .. ' ' .. ent:talk()").tojstring());
	}
}
