# luaj-classlib

Classes for Lua scripts running on [LuaJ](https://github.com/dimmskii/luaj).

Lua doesn't come with classes, and every project ends up building its own out of tables and metatables.
This library gives your scripts one small, consistent way to do it: you describe a class as a plain table,
register it, and from then on you can make instances of it, extend it, call up into the class you extended,
and keep changing it while your program runs. It's written in Java, sits next to LuaJ's own libraries, and
works the same whether your scripts are interpreted or compiled to Java bytecode with LuaJC.

## Getting started

Add the library to your project (it brings LuaJ with it), then load it into your globals the same way you'd
load any of LuaJ's libraries:

```java
Globals globals = JsePlatform.standardGlobals();
globals.load(new ClassLib());   // org.luaj.vm2.lib.ClassLib
```

That gives every script a few new globals: `class` (with `class.register`, `class.get` and a few ways to look
around), `new`, `super`, `isclass`, `isobj` and `instanceof`. The sections below walk through what they're
for, one idea at a time.

### 1. Registering classes

A class starts out as an ordinary Lua table. It needs a name, under `__classname`, and whatever methods and
fields you want it to have. Handing it to `class.register` turns it into a class:

```lua
local Animal = class.register({
    __classname = "animal",
    speak = function(self) return "..." end,
})
```

`class.register` gives you back a new table, the class itself. The table you passed in is only read, never
kept, so you can put it together in whichever way reads best. Building it up as a local first works well
for longer classes:

```lua
local Cat = { __classname = "cat" }

function Cat:speak()
    return "Meow"
end

Cat = class.register(Cat)
```

If you're used to Garry's Mod style entity files, where a file fills in a global table that something else
then registers, that works too. The global is just the table, and you can clear it once you're done:

```lua
ENT = {}
require("entities.lamp")          -- the file sets ENT.__classname, ENT.switch, ...
local Lamp = class.register(ENT)
ENT = nil
```

Or give each class its own file that returns the registered class, and load it with `require` like any
other module:

```lua
-- weapon/gun.lua
return class.register({
    __classname = "weapon.gun",
    fire = function(self) return "bang" end,
})
```

```lua
local Gun = require("weapon.gun")
```

Sometimes you don't have anything to put in the table yet. Then just give `class.register` the name: you get
an empty class, and you can add its methods afterwards, with the familiar colon syntax:

```lua
local Bird = class.register("bird")

function Bird:speak()
    return "Tweet"
end
```

Once a class is registered you can also find it again by name, from anywhere: `class.get("weapon.gun")`.

### 2. Making instances

To make an instance, call the class like a function. Anything you pass goes to its constructor (see
[Constructors](#7-constructors)):

```lua
local a = Animal()
print(a:speak())                  -- ...
```

The global `new` does the same, and it can also find the class by its name, which is handy when all you have
is the name:

```lua
local b = new(Animal)
local c = new("animal")           -- the class registered under that name
```

An instance is a table of its own. Whatever you set on it belongs to that instance; methods and the class's
fields are looked up on the class. `isclass` and `isobj` tell you what you're holding, and are happy to be
asked about any value:

```lua
a.nickname = "Rex"
print(isobj(a), isclass(Animal), isobj(Animal), isclass(42))   -- true true false false
```

### 3. Instance data and masking

Each instance keeps its own data. Setting a field on an instance stores it on that instance only, and if the
class has a member of the same name, the instance's value masks it, for that instance alone. That includes
`nil`: an instance you set something to `nil` on reads `nil`, even if its class has a value there. To take
the class's value again, assign it:

```lua
local Lamp = class.register({ __classname = "lamp", color = "white" })
local a, b = Lamp(), Lamp()

a.color = "red"
print(a.color, b.color, Lamp.color)   -- red   white   white
a.color = nil
print(a.color)                        -- nil: still masked, now with nil
a.color = Lamp.color
print(a.color)                        -- white: a copy of the class's value, now a's own
```

Since that last line copies the value, `a` keeps `"white"` even if `Lamp.color` changes later; instances
that never set `color` follow the class.

The fields the library keeps (`__classname`, `__shortname`, `__basename`, `__class` and `__base`) can't be
masked: setting one on an instance raises an error, just like setting it on a class.

### 4. Names and namespaces

Class names are case-sensitive (`"Dog"` and `"dog"` would be two different classes), and dots split a name
into parts, the way a package path does: `"game.weapon.pistol"`. The last part is the class's short name,
which the library works out for you. It's there to use however you like, as a display name for example:

```lua
local Pistol = class.register({ __classname = "game.weapon.pistol" })
print(Pistol.__classname, Pistol.__shortname)    -- game.weapon.pistol   pistol
```

Because names are paths, you can make them line up with where things really live. Name each class after its
module path (so `game.weapon.pistol` lives in `game/weapon/pistol.lua`), and if you like, hang the classes
off the global namespace under the same path:

```lua
local function expose(cls)
    local at = _G
    for part in cls.__classname:gmatch("([^.]+)%.") do   -- every part but the last
        at[part] = at[part] or {}
        at = at[part]
    end
    at[cls.__shortname] = cls
end

expose(Pistol)
local p = new(game.weapon.pistol)
```

### 5. Inheritance

A class can extend another one. Give `class.register` the base as a second argument, either the class
itself or its name, or name it inside the table under `__basename` (if you do both, the argument wins). The
base has to be registered first, which `require` takes care of naturally if each class file requires its
base at the top:

```lua
local Dog = class.register({
    __classname = "dog",
    __basename = "animal",
    fetch = function(self) return "fetched!" end,
})

local d = Dog()
print(d:speak(), d:fetch())       -- ...   fetched!

local Wolf = class.register("wolf", Dog)        -- or class.register("wolf", "dog")
```

The subclass gets everything its base has, and anything it defines itself takes the place of the base's
version. There's no need for one common ancestor: any class can stand on its own, and you can have as many
separate hierarchies as you want. The only rules are that a class can't end up inheriting itself, and that
its base must already be registered (so it can't happen by accident).

### 6. Base calls (super)

Inside a method, `super` reaches the version of a method in the class you extended. Call it with a dot and
pass `self` along:

```lua
local Puppy = class.register({
    __classname = "puppy",
    __basename = "dog",
    speak = function(self)
        return "Yip! (a puppy, but also " .. super.speak(self) .. ")"
    end,
})
```

`super` always goes up from the class that *wrote* the method, not from the instance's class. That's what
lets every class in a long chain call up safely: each call goes exactly one level up, and classes that don't
have their own version are simply skipped. It works the same in interpreted and LuaJC-compiled scripts,
through coroutines that yield halfway through a method, and through errors.

### 7. Constructors

A class can have a constructor. Its name is just `__`, so that it reads like the class itself: it runs on
every new instance and receives whatever the class was called with:

```lua
local Gun = class.register("weapon.gun")

function Gun:__(ammo)
    self.ammo = ammo or 0
end

local Rifle = class.register("weapon.rifle", Gun)

function Rifle:__(ammo, scope)
    super.__(self, ammo)
    self.scope = scope
end

local r = Rifle(30, "4x")         -- r.ammo == 30, r.scope == "4x"
```

Constructors are inherited like any other method, so a class without its own simply uses its base's. A class
with no constructor anywhere above it still has one that quietly does nothing, which means `super.__` is
always safe to call. If you write the constructor inside the table instead, remember to declare `self` as its
first parameter (`__ = function(self, ammo) ... end`): there's no colon syntax there to add it for you.
Usage of constructors is completely optional.

### 8. Mutability

Classes aren't frozen once they're registered. You can add, replace or remove methods at any time, and the
change reaches every subclass that doesn't have its own version:

```lua
Animal.sleep = function(self) return "zzz" end
print(Puppy():sleep())            -- zzz: Dog and Puppy picked it up

Dog.speak = function(self) return "Woof" end
print(Puppy():speak())            -- Yip! (a puppy, but also Woof)

Dog.speak = nil                   -- Dog gives up its own version again...
print(Puppy():speak())            -- ...and Puppy's super call reaches Animal's: Yip! (a puppy, but also ...)
```

Methods you add later work with `super` just like the ones the class was registered with, so a class in the
middle of a chain can start overriding something and slot straight into the chain of supercalls.

This does make writing to a class table a little more expensive than writing to a plain table: each change is
carried down to the subclasses that inherit it. Reading a method, calling it and making instances cost the
same as before, so the trade-off is worth it in practice, but it's better to set up classes up front than to
rewrite their methods in a hot loop.

The fields the library keeps for you (`__classname`, `__shortname`, `__basename`, `__class` and `__base`)
are read-only: setting one raises an error.

### 9. Overriding methods on one instance

Because an instance can mask anything its class has, it can also replace a method for itself alone. It's the
least advisable kind of change: the class no longer decides what that instance does, and a plain function
put in place of an inherited method loses the use of `super` (called directly it raises an error; called from
one of the class's own methods it can reach the wrong class). If you do it, keep the class's version and call
that instead of `super`:

```lua
local ent = Puppy()
ent.old_speak = ent.speak                     -- the class's method, kept
ent.speak = function(self)
    return "*wags* " .. self:old_speak()      -- not super.speak(self)
end
print(ent:speak())                            -- *wags* Yip! (a puppy, but also ...)
```

The kept method still runs as the class's own, so the `super` calls inside it work as usual. Metamethods
are the exception to all this: they always come from the class, so setting one on a single instance has no
effect.

### 10. Operators and other metamethods

A class is its instances' metatable, so any metamethod you give a class works on its instances: printing them,
adding them together, comparing them, calling them. Like other members, metamethods are inherited and can be
changed later:

```lua
local Vec = class.register("vec")
function Vec:__(x, y) self.x, self.y = x, y end
Vec.__tostring = function(v) return "(" .. v.x .. ", " .. v.y .. ")" end
Vec.__add = function(a, b) return Vec(a.x + b.x, a.y + b.y) end
Vec.__eq = function(a, b) return a.x == b.x and a.y == b.y end

print(Vec(1, 2) + Vec(3, 4))                   -- (4, 6)
print(Vec(1, 2) == Vec(1, 2))                  -- true
```

(Calling the class itself always makes an instance; a `__call` you define makes the *instances* callable.)

### 11. Looking around

A few calls let you ask about the classes you have. `class.list` gives the names of the registered classes,
sorted, and can stay inside one namespace; `class.subclasses` gives the classes that extend one (with `true`,
all the way down):

```lua
print(table.concat(class.list("weapon"), ", "))          -- weapon.gun, weapon.rifle
for _, c in ipairs(class.subclasses(Animal, true)) do
    print(c.__classname)                                  -- dog, wolf, puppy
end
```

`instanceof` asks whether an instance is of a class or of anything that extends it, and `class.extends`
asks the same about two classes. Pass `true` as a third argument to ask about that exact class only:

```lua
local p = Puppy()
print(instanceof(p, Dog), instanceof(p, "animal"), instanceof(p, Dog, true))   -- true true false
print(class.extends(Puppy, Animal), class.extends(Animal, Puppy))              -- true false
```

### 12. When something goes wrong

Mistakes are reported as ordinary Lua errors that say what's wrong, so you can catch them with `pcall` like
any other:

```lua
print(pcall(class.register, { __classname = "animal" }))
-- false   class 'animal' is already registered

print(pcall(class.register, { __classname = "cat.", __basename = "animal" }))
-- false   class __classname 'cat.' must not end with '.'

print(pcall(new, "unicorn"))
-- false   bad argument #1 (no class registered as 'unicorn')

print(pcall(instanceof, Animal, Animal))
-- false   bad argument #1 to 'instanceof' (instance expected, got class)
```

### Trying it yourself

The repository has a walkthrough script that shows all of this working, with its classes split into files
the way you'd do it in a real project. With JDK 17 or later:

```sh
./gradlew runLua --args="scripts/demo.lua"
```

## Installing

Builds come from [JitPack](https://jitpack.io/#dimmskii/luaj-classlib), straight from this repository. There
are no release tags yet, so use `main-SNAPSHOT` (the latest commit on `main`) until there are; after that,
use a tag such as `0.1.0` instead. The library runs on Java 8 and later, and brings LuaJ
([luaj-jitpack](https://github.com/dimmskii/luaj-jitpack)) with it.

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.dimmskii:luaj-classlib:main-SNAPSHOT")
}
```

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.dimmskii</groupId>
    <artifactId>luaj-classlib</artifactId>
    <version>main-SNAPSHOT</version>
</dependency>
```

## Reference: intended behaviour

The same ground as [Getting started](#getting-started), stated precisely. Where the library doesn't do
what's written here yet, [TODO.md](TODO.md) lists it.

**Installing.** `globals.load(new ClassLib())` sets the globals `class` (also `package.loaded.class`), `new`,
`super`, `isclass`, `isobj` and `instanceof`. `class` holds `class.register`, `class.get`, `class.list`,
`class.subclasses` and `class.extends`.

**Defining a class.** `class.register(class [, base])`. `class` is a plain table (its `__classname`, a string,
and its methods and fields, and optionally its base's name, `__basename`), or just the class's name, which
makes an empty class. `base` is a class or a registered class's name; when given, it's the base whatever the
table's `__basename` says, and when nil (given or left out) the base is the one `__basename` names, or none.
`class.register` returns the class table. That's a new table: the one passed in is only read, so a script can
build it however it likes (a local, a global like `ENT` filled by another file) and drop it after.

**Names.** A class's name is its `__classname`, exactly as written: names are case-sensitive (`"Dog"` and
`"dog"` are two classes). `.` is a delimiter (`"weapon.pistol"`), so a name can't be empty, start or end
with a dot, or have two dots in a row; the part after the last dot is the class's short name (`__shortname`:
`"pistol"`). Each name is registered once. `class.get(name)` returns the class
registered under exactly that name, or nil.

**Hierarchy.** Classes don't have to share a common ancestor: any class, at any point in a hierarchy, may
have no base (nil), and there can be any number of root classes. The only rules:

1. A class may not inherit itself, directly or indirectly.
2. When a class is registered, its base class (and so all of its ancestors) must already be registered.

Both hold by construction: a base must already be registered when it's named, and a class only exists once
its own `class.register` returns, so no chain can come back to it.

**Inheritance.** A class holds its own members (its table's) and copies of its base's other members; its own
replace the base's of the same name. Classes stay mutable after registering, and the copies stay up to date:
setting a member on a class (`B.f = function(self) ... end`, or `rawset`) makes it the class's own, and it
reaches every subclass that doesn't define that member itself; setting it to nil gives it up, so the class
(and those subclasses) inherit the base's again. A class registered later copies its base's members as they
are then. Functions set on a class become its methods, so `super` works in them too. Reads are copies, as
fast as plain table reads; a write costs one update per subclass it reaches.

**Metamethods.** A class is its instances' metatable, so the metamethods it defines (`__tostring`, `__add`,
`__eq`, `__call`...) apply to its instances, and are inherited and changed like any other member.

**Instances.** Calling a class, `Class(...)`, makes an instance, and so do `new(Class, ...)` and `new("name",
...)` (the class registered by that name): a table of its own, whose metatable is its class (with `__index`
the class). The arguments go to the constructor. Fields set on an instance are the instance's, and mask the
class's member of that name for that instance only, nil included (it reads nil; assigning the class's value
takes a copy of it). Setting nil for a key the class doesn't have removes it. Methods and the class's fields
are found on the class. The system fields can't be set on an instance. Metamethods always come from the class.
`isclass(value)` and `isobj(value)` tell whether a value is a class or an instance (false for anything else).

**Constructors.** A class's `__` is its constructor: a method that runs on each new instance, given the
arguments the instance was made with, before the instance is returned (what it returns is ignored).

```lua
local Gun = class.register("weapon.gun")
function Gun:__(ammo) self.ammo = ammo end
local Pistol = class.register("weapon.pistol", Gun)
function Pistol:__(ammo, silenced) super.__(self, ammo) self.silenced = silenced end
local p = Pistol(12, true)   -- p.ammo == 12, p.silenced == true
```

Constructors are copied into subclasses like any other method: a class without its own `__` has its
nearest ancestor's, and `super.__` reaches the nearest ancestor that has one. A class with no constructor at
all (none of its own, none above it) has its own default constructor, which does nothing, so every class has
a `__`, and a constructor can always call `super.__` when its class has a base. What making an instance costs
is one call to the class's constructor plus the supercalls constructors make: classes without one of their
own add nothing.

**System fields.** Every class has these, and its instances see them through the class. The library keeps
them; scripts read them, and setting one on a class raises an error.

| Field | Is |
|---|---|
| `__classname` | the class's name, as registered (`"weapon.pistol"`) |
| `__shortname` | the last part of its name, after the last dot (`"pistol"`); the whole name if it has none |
| `__basename` | its base's `__classname`, `""` for a class with no base |
| `__class` | the class table itself |
| `__base` | the base class table, nil for a class with no base |

**super.** Inside a class's method, `super.name` is `name` in the base of the class *the method was
registered with*, not the instance's class: so every level of a chain of supercalls goes one class up, and
classes that don't implement the method are skipped. Call it with a dot and pass `self`:
`super.speak(self, ...)`. It works for interpreted functions and ones compiled with LuaJC, through tail
calls, errors and coroutines (a method can yield in the middle). It's only valid while a method runs: used
outside one (including from a callback called after its method returned), or in a class with no base, it
raises a Lua error. The methods are the functions a class was registered with or that were set on it since.

**Queries.** `class.list([prefix])` returns the registered class names, sorted, as an array; with a prefix,
only those under it as a dotted name (`"game.weapon"` includes `game.weapon.gun`, not `game.weaponry`).
`class.subclasses(C [, all])` returns the classes registered with `C` as their base, or with `all` every
class below it. `class.extends(A, B [, exact])` tells whether class `A` is `B` or extends it, and
`instanceof(instance, C [, exact])` whether an instance is of `C` or a subclass; with `exact`, only that very
class counts. Classes can be given by name.

**Errors.** Misuse (a missing, non-string or invalid `__classname`, a `__` that isn't a function, a
`__basename` or base argument that isn't a string or a class or isn't registered, a third argument to
`class.register`, setting a system field on a class or an instance, `new` on something that isn't a class or a
registered name, a name registered twice, the wrong kind of argument to the queries) raises a Lua error naming
the problem. The queries word theirs as Lua does: `bad argument #1 to 'instanceof' (instance expected, got
class)`.

## Building

Needs JDK 17 or later (to run Gradle); the jar targets Java 8.

```sh
./gradlew build              # tests, then jar, sources and javadoc jars in build/libs
./gradlew test               # the JUnit tests alone (src/test/java)
./gradlew publishToMavenLocal  # into ~/.m2 as io.github.dimmskii:luaj-classlib:<version>
```

The version for local builds is in `gradle.properties`; dependency versions are in
`gradle/libs.versions.toml`.

**Trying it.** `./gradlew runLua --args="scripts/demo.lua"` runs a Lua file with LuaJ and the class library
loaded (without arguments, `scripts/main.lua`, a playground). `scripts/demo.lua` walks through everything
above, checking itself as it goes; its classes are files under `scripts/demo/`, loaded with `require`. The
runner is `LuaJClassLibTest` in the test sources, so it isn't part of the jar.

**Tests.** `ClassLibTest` covers the intended behaviour above (registering, inheritance, mutability,
instances, the queries, constructors); `SuperTest` runs `super` both interpreted and compiled with LuaJC
(which the tests get BCEL for); `RegistrationStylesTest` builds the same hierarchy every supported way and
checks it in both modes; `LuaClassManagerTest` uses the manager from Java.

## Releasing

Tag a commit on `main` with the version and push the tag; JitPack builds it the first time it's
requested (log: `https://jitpack.io/com/github/dimmskii/luaj-classlib/<tag>/build.log`).

```sh
git tag 0.1.0
git push origin 0.1.0
```

## License

MIT, see [LICENSE](LICENSE).
