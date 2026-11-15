/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/
 */
package com.cobblemon.mod.common.api.ai

import com.google.common.collect.ImmutableList
import com.mojang.datafixers.util.Pair
import net.minecraft.world.attribute.AttributeTypes
import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.phys.AABB

/** The old two-argument activity registration had no additional conditions or memories to erase. */
fun registerUnconditionalActivity(brain: Brain<*>, activity: Activity, tasks: List<Pair<Int, BehaviorControl<in LivingEntity>>>) {
    brain.addActivity(activity, ImmutableList.copyOf(tasks), emptySet(), emptySet())
}

/** Server AI query preserves the old player-position AABB test and non-combat visibility predicate. */
fun nearbySleepPlayers(sleeper: LivingEntity): List<Player> {
    val level = sleeper.level() as ServerLevel
    val area = AABB.ofSize(sleeper.position(), 16.0, 16.0, 16.0)
    val targeting = TargetingConditions.forNonCombat()
    return level.players().filter { area.contains(it.position()) && targeting.test(level, sleeper, it) }
}

/** Native replacement for Schedule.EMPTY: constant IDLE, independent of villager biome/timeline schedules. */
val IDLE_BRAIN_SCHEDULE: EnvironmentAttribute<Activity> = EnvironmentAttribute.builder(AttributeTypes.ACTIVITY)
    .defaultValue(Activity.IDLE).notPositional().build()
