package org.luaj.vm2.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The same hierarchy, zoo.animal <- zoo.dog <- zoo.puppy, registered in every supported way, must behave the same:
 * constructors, overrides, super, the class queries and mutability. Each style runs interpreted and with LuaJC.
 */
class RegistrationStylesTest extends ClassLibTestBase {

	/** Each style defines the locals Animal, Dog and Puppy. */
	private static final String[][] STYLES = {
		{ "tables naming their base", ""
			+ "local Animal = class.register({ __classname = 'zoo.animal', __ = function(self, name) self.name = name end,"
			+ "  speak = function(self) return '...' end })"
			+ " local Dog = class.register({ __classname = 'zoo.dog', __basename = 'zoo.animal',"
			+ "  __ = function(self, name, trick) super.__(self, name) self.trick = trick end,"
			+ "  speak = function(self) return 'woof>' .. super.speak(self) end })"
			+ " local Puppy = class.register({ __classname = 'zoo.puppy', __basename = 'zoo.dog' })" },
		{ "tables, base as a class argument", ""
			+ "local Animal = class.register({ __classname = 'zoo.animal', __ = function(self, name) self.name = name end,"
			+ "  speak = function(self) return '...' end })"
			+ " local Dog = class.register({ __classname = 'zoo.dog',"
			+ "  __ = function(self, name, trick) super.__(self, name) self.trick = trick end,"
			+ "  speak = function(self) return 'woof>' .. super.speak(self) end }, Animal)"
			+ " local Puppy = class.register({ __classname = 'zoo.puppy' }, Dog)" },
		{ "tables, base as a name argument", ""
			+ "local Animal = class.register({ __classname = 'zoo.animal', __ = function(self, name) self.name = name end,"
			+ "  speak = function(self) return '...' end })"
			+ " local Dog = class.register({ __classname = 'zoo.dog', __basename = 'ignored.by.the.argument',"
			+ "  __ = function(self, name, trick) super.__(self, name) self.trick = trick end,"
			+ "  speak = function(self) return 'woof>' .. super.speak(self) end }, 'zoo.animal')"
			+ " local Puppy = class.register({ __classname = 'zoo.puppy' }, 'zoo.dog')" },
		{ "names, members added after with the colon syntax", ""
			+ "local Animal = class.register('zoo.animal')"
			+ " function Animal:__(name) self.name = name end function Animal:speak() return '...' end"
			+ " local Dog = class.register('zoo.dog', 'zoo.animal')"
			+ " function Dog:__(name, trick) super.__(self, name) self.trick = trick end"
			+ " function Dog:speak() return 'woof>' .. super.speak(self) end"
			+ " local Puppy = class.register('zoo.puppy', 'zoo.dog')" },
		{ "names, base as a class argument", ""
			+ "local Animal = class.register('zoo.animal')"
			+ " Animal.__ = function(self, name) self.name = name end Animal.speak = function(self) return '...' end"
			+ " local Dog = class.register('zoo.dog', Animal)"
			+ " Dog.__ = function(self, name, trick) super.__(self, name) self.trick = trick end"
			+ " Dog.speak = function(self) return 'woof>' .. super.speak(self) end"
			+ " local Puppy = class.register('zoo.puppy', Dog)" },
		{ "a global table filled in, then registered", ""
			+ "ENT = {} ENT.__classname = 'zoo.animal' function ENT:__(name) self.name = name end function ENT:speak() return '...' end"
			+ " local Animal = class.register(ENT)"
			+ " ENT = {} ENT.__classname = 'zoo.dog' ENT.__basename = 'zoo.animal'"
			+ " function ENT:__(name, trick) super.__(self, name) self.trick = trick end"
			+ " function ENT:speak() return 'woof>' .. super.speak(self) end"
			+ " local Dog = class.register(ENT)"
			+ " ENT = { __classname = 'zoo.puppy' } local Puppy = class.register(ENT, Dog) ENT = nil" },
	};

	static Stream<Arguments> styles() {
		Stream.Builder<Arguments> out = Stream.builder();
		for (String[] style : STYLES) {
			for (boolean luajc : new boolean[] { false, true }) {
				out.add(Arguments.of(style[0], style[1], luajc));
			}
		}
		return out.build();
	}

	private String run(String style, boolean luajc, String check) {
		globals = globals(luajc);
		return eval(style + " " + check).tojstring();
	}

	@ParameterizedTest(name = "{0}, luajc={2}")
	@MethodSource("styles")
	void constructorsChainAndAreInheritedWhenTheClassIsCalled(String name, String style, boolean luajc) {
		assertEquals("rex roll | bo nil | pip nil", run(style, luajc,
				"local p, d, a = Puppy('rex', 'roll'), new('zoo.dog', 'bo'), new(Animal, 'pip')"
				+ " return p.name .. ' ' .. tostring(p.trick) .. ' | ' .. d.name .. ' ' .. tostring(d.trick)"
				+ " .. ' | ' .. a.name .. ' ' .. tostring(a.trick)"));
	}

	@ParameterizedTest(name = "{0}, luajc={2}")
	@MethodSource("styles")
	void overridesAndSuperWork(String name, String style, boolean luajc) {
		assertEquals("woof>... woof>... ...", run(style, luajc,
				"return Puppy():speak() .. ' ' .. Dog():speak() .. ' ' .. Animal():speak()"));
	}

	@ParameterizedTest(name = "{0}, luajc={2}")
	@MethodSource("styles")
	void theQueriesSeeTheSameHierarchy(String name, String style, boolean luajc) {
		assertEquals("zoo.animal zoo.dog zoo.puppy | zoo.dog zoo.puppy | true true false | true zoo.animal", run(style, luajc,
				"local o = {} for i, c in ipairs(class.subclasses(Animal, true)) do o[i] = c.__classname end"
				+ " local p = Puppy()"
				+ " return table.concat(class.list('zoo'), ' ') .. ' | ' .. table.concat(o, ' ') .. ' | '"
				+ " .. tostring(class.extends(Puppy, 'zoo.animal')) .. ' ' .. tostring(instanceof(p, Dog)) .. ' '"
				+ " .. tostring(instanceof(p, Dog, true)) .. ' | ' .. tostring(Puppy.__base == Dog) .. ' ' .. Dog.__basename"));   // the base argument wins over a table's __basename
	}

	@ParameterizedTest(name = "{0}, luajc={2}")
	@MethodSource("styles")
	void changesReachTheWholeHierarchy(String name, String style, boolean luajc) {
		assertEquals("zzz | woof>... | yip>woof>... | woof>...", run(style, luajc,
				"Animal.sleep = function(self) return 'zzz' end"                                  // added to the root
				+ " local slept = Puppy():sleep()"
				+ " local before = Puppy():speak()"
				+ " Puppy.speak = function(self) return 'yip>' .. super.speak(self) end"            // the leaf gains an override
				+ " local after = Puppy():speak() Puppy.speak = nil"                               // and gives it up
				+ " return slept .. ' | ' .. before .. ' | ' .. after .. ' | ' .. Puppy():speak()"));
	}
}
