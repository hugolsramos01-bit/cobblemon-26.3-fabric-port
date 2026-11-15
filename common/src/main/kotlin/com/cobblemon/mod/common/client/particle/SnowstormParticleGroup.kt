/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.particle

import com.cobblemon.mod.common.client.render.SnowstormParticle
import net.minecraft.client.Camera
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.ParticleGroup
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState

class SnowstormParticleGroup(engine: ParticleEngine) : ParticleGroup<SnowstormParticle>(engine) {
    override fun extractRenderState(frustum: Frustum, camera: Camera, partialTick: Float): ParticleGroupRenderState {
        // Freeze a fresh frame. Render submission must never retain live particles,
        // their MoLang runtimes, emitter matrices or mutable tint/UV objects.
        val state = SnowstormParticleRenderState()
        particles.forEach { particle ->
            if (!particle.invisible && frustum.isVisible(particle.boundingBox)) {
                particle.extract(state, camera, partialTick)
            }
        }
        return state
    }
}
