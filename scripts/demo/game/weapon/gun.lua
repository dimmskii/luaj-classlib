-- game.weapon.gun: extends game.entity. Requiring the base first registers it first (it must be).
require("game.entity")

return class.register({
    __classname = "game.weapon.gun",
    __basename = "game.entity",
    __ = function(self, name, ammo)
        super.__(self, name)
        self.ammo = ammo or 0
    end,
    fire = function(self)
        if self.ammo == 0 then return "click" end
        self.ammo = self.ammo - 1
        return "bang"
    end,
})
