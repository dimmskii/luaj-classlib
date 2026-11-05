-- game.entity: a root class (no __basename). One file per class, returning the registered class.
return class.register({
    __classname = "game.entity",
    __ = function(self, name)
        self.name = name or "nameless"
    end,
    describe = function(self)
        return self.__shortname .. " '" .. self.name .. "'"
    end,
})
