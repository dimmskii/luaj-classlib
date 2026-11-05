# TODO

Open work on luaj-classlib, beside the intended behaviour in the [README](README.md#intended-behaviour).

## Known bugs

Where the library doesn't do what the README says yet.

None known.

## Potential gaps

Not decided: behaviour that may need changing.

- **`pairs` over a class shows some system fields, not others.** It lists `__classname`, `__shortname`,
  `__basename` and `__index` (stored in the table) but not `__class` or `__base` (answered by
  `ClassLuaTable.hashget` only).
  Either store all of them or none.
- **`getmetatable(instance)` is the class table itself**, so any script holding an instance can change its
  class. A `__metatable` field on class tables would hide it, if classes should be protected.

- **`super` inside a plain function set on an instance** (`obj.f = function(self) ... super.f(self) end`).
  Such a function isn't a method of any class, so `super` raises "not inside a class method" when it's
  called directly, but when one of the class's own methods calls it, `super` resolves against that method's
  class instead, which can reach the wrong class silently. The README advises keeping the class's version
  (`obj.old_f = obj.f`) and calling that. Undecided: make `super` a consistent error there (instances wrap
  the plain functions they store as "instance functions", and `super` refuses them), or leave it documented.

## Later

- **A default `__tostring` for instances** (`dog: 0x...` instead of `table: 0x...`; LuaJ doesn't name
  tables by `__name` the way Lua 5.3+ does). Classes can already give their instances any metamethod by
  defining it (README, "Operators and other metamethods"); this is only about the default.
- **`__instanceof` on every class**, so an instance can be asked directly: `obj:__instanceof(Dog [, exact])`,
  the same answer as `instanceof(obj, Dog [, exact])`. It belongs on the class for the same reason the
  constructor `__` does: it's an operation on instances, and the class is their metatable, so every instance
  reaches it through `__index` without anything stored on the instance.

  How it would work:
  - **One function per class, made once.** `ClassLuaTable` holds it in a field, created with the class (like
    its default constructor), so `Dog.__instanceof == Dog.__instanceof` and a lookup allocates nothing.
  - **Answered, not stored.** `ClassLuaTable.hashget` returns that field for the key, as a `case` beside the
    system fields (`__classname`, `__class`, ...). Nothing goes into the table itself, so registering doesn't
    copy it into subclasses (`updateInheritance` only copies what's stored), `pairs` doesn't list it, and
    changes never need to be carried down: each class answers its own.
  - **Reserved.** Its key joins `LuaClassManager.SYSTEM_KEYS`, so setting it on a class raises the same
    "can't be set" error as the other system fields, and a table's own `__instanceof` is skipped when it
    registers.
  - **The function itself** takes `self` (must be an instance: `bad argument #1 to '__instanceof' (instance
    expected, got class)` otherwise), a class or class name, and an optional `exact`, and returns
    `LuaClassManager.isInstance(self, class, exact)`.

  Open: whether classes may override it (reserved as above, or answered only while the class defines none,
  the way the default constructor is). An instance's own field of that name would shadow it, as with any
  method.

  Name: something shorter than `__instanceof`, since it's called as a method: **`__is`** or **`__isa`**
  (`if obj:__is(Dog) then`). The global `instanceof` stays as it is.
