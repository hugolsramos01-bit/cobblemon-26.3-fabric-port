/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.permission

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionValidator
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.permissions.Permissions
import net.minecraft.server.level.ServerPlayer

/**
 * A [PermissionValidator] that uses the permission level vanilla system.
 * This is only used when the platform has no concept of permissions.
 */
class LaxPermissionValidator : PermissionValidator {

    override fun initialize() {
        Cobblemon.LOGGER.info("Booting LaxPermissionValidator, permissions will be checked using Minecrafts permission level system, see https://minecraft.wiki/w/Permission_level")
    }

    override fun hasPermission(player: ServerPlayer, permission: Permission) = player.permissions().hasPermissionAtLevel(permission.level.numericalValue)
    override fun hasPermission(source: CommandSourceStack, permission: Permission) = source.permissions().hasPermissionAtLevel(permission.level.numericalValue)
    override fun hasPermission(player: ServerPlayer, permission: String, level: Int) = player.permissions().hasPermissionAtLevel(level)
    override fun hasPermission(source: CommandSourceStack, permission: String, level: Int) = source.permissions().hasPermissionAtLevel(level)

    private fun PermissionSet.hasPermissionAtLevel(level: Int) = when {
        level <= 0 -> true
        level == 1 -> hasPermission(Permissions.COMMANDS_MODERATOR)
        level == 2 -> hasPermission(Permissions.COMMANDS_GAMEMASTER)
        level == 3 -> hasPermission(Permissions.COMMANDS_ADMIN)
        level == 4 -> hasPermission(Permissions.COMMANDS_OWNER)
        else -> false
    }
}
