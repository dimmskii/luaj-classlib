-- Runs with LuaJ's standard libraries and luaj-classlib's `class` library already in the globals.
print(_VERSION, "| class library:", class ~= nil, "| package library:", package ~= nil)

-- Set package search path to scripts as well
package.path = package.path .. ";./scripts/?.lua;./?.lua"


---------------------------------------------------------------
-- Inline examples
---------------------------------------------------------------
-- A class: a plain table of methods, bound at the end.
local CAnimal = {
    __classname="Animal"
}
function CAnimal:speak() return "..." end
CAnimal = class.register(CAnimal)
-- A subclass, extending Animal.
local CDog = {
    __classname="Dog",
    __basename="Animal"
}
function CDog:speak() return "Woof" end
CDog = class.register(CDog)

local dogInstance = new(CDog)
print("Dog says", dogInstance:speak())

-- Arguments after the script name: ./gradlew run --args="scripts/main.lua one two"
if arg and #arg > 0 then print("arguments:", table.concat(arg, ", ")) end




---------------------------------------------------------------
-- Oldschool Risingear way inspired by GMod
-- you supply the global beforehand and register after it runs
---------------------------------------------------------------
ENT = {}
require("class_a_oldschool")
CEntity = class.register( ENT )
ENT = nil -- Clear from globals whenever you're done all of these
local ent1 = new(CEntity) -- Should work
ent1:info()

---------------------------------------------------------------
-- Neat file. Uses its own local table inside itself,
-- then the file itself returns the class registry result
---------------------------------------------------------------
local ent2 = require("class_b_neatfile")
ent2:info()


-- Attempt to write readonly info: system fields can't be set
print("readonly:", pcall(function() ent2.__class = {} end))


---------------------------------------------------------------
-- super: five classes, each extending the one before. 1st, 3rd
-- and 5th implement hello; 3rd and 5th call down with super.
-- hello on a 5th prints three lines: 5th, 3rd, 1st (2nd and 4th
-- implement nothing, so super skips them).
---------------------------------------------------------------
local C1 = class.register({ __classname = "c1", hello = function(self) print("hello from c1 (instance of " .. self.__classname .. ")") end })
local C2 = class.register({ __classname = "c2", __basename = "c1" })
local C3 = class.register({ __classname = "c3", __basename = "c2", hello = function(self) print("hello from c3") super.hello(self) end })
local C4 = class.register({ __classname = "c4", __basename = "c3" })
local C5 = class.register({ __classname = "c5", __basename = "c4", hello = function(self) print("hello from c5") super.hello(self) end })
new(C5):hello()



print ("--------------------------------1")
local ClassOne = class.register(
    {
        __classname="package.frackage.ClassOne",
        __=function(self)
            print(self.__shortname)
        end
    }
)
ClassOne()
print ("--------------------------------2")
local ClassTwo = class.register(
    {
        __classname="package.frackage.ClassTwo",
        __basename="package.frackage.ClassOne"
    }
)
ClassTwo()
print ("--------------------------------3")
local ClassThree = class.register(
    {
        __classname="package.frackage.ClassThree",
        __basename="package.frackage.ClassTwo",
        __=function(self)
            print("boop")
            super.__(self)
        end
    }
)
ClassThree()
