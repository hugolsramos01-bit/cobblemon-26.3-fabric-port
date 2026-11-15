/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.orientation.OrientationController;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.HoverBehaviour;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.duck.PlayerDuck;
import com.cobblemon.mod.common.duck.RidePassenger;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData;
import com.cobblemon.mod.common.pokedex.scanner.ScannableEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.CompoundTagUtilities;
import com.cobblemon.mod.common.util.DataKeys;
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements ScannableEntity, OrientationControllable, PlayerDuck, RidePassenger {

    @Unique
    private static final EntityDataAccessor<String> COBBLEMON_SHOULDER_LEFT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.STRING);
    @Unique
    private static final EntityDataAccessor<String> COBBLEMON_SHOULDER_RIGHT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.STRING);

    @Unique
    private Vector3f cobblemon$driverInput;
    @Unique
    private Vector3f cobblemon$lastSentDriverInput;

    @Shadow public abstract boolean addItem(ItemStack stack);

    @Shadow protected int jumpTriggerTime;
    @Unique private final OrientationController cobblemon$orientationController = new OrientationController(this);

    @Unique private float cobblemon$rideXRot = 0.0f;

    @Unique private float cobblemon$rideYRot = 0.0f;

    @Unique private Vec3 cobblemon$rideEyePos = Vec3.ZERO;

    protected PlayerMixin(EntityType<? extends LivingEntity> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void cobblemon$defineShoulderData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(COBBLEMON_SHOULDER_LEFT, "{}");
        builder.define(COBBLEMON_SHOULDER_RIGHT, "{}");
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void cobblemon$readShoulderData(ValueInput input, CallbackInfo ci) {
        input.read("CobblemonShoulderLeft", CompoundTag.CODEC)
            .ifPresent(tag -> this.entityData.set(COBBLEMON_SHOULDER_LEFT, tag.toString()));
        input.read("CobblemonShoulderRight", CompoundTag.CODEC)
            .ifPresent(tag -> this.entityData.set(COBBLEMON_SHOULDER_RIGHT, tag.toString()));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void cobblemon$writeShoulderData(ValueOutput output, CallbackInfo ci) {
        output.store("CobblemonShoulderLeft", CompoundTag.CODEC, cobblemon$getShoulderPokemon(true));
        output.store("CobblemonShoulderRight", CompoundTag.CODEC, cobblemon$getShoulderPokemon(false));
    }

    @Override
    public CompoundTag cobblemon$getShoulderPokemon(boolean isLeft) {
        String data = this.entityData.get(isLeft ? COBBLEMON_SHOULDER_LEFT : COBBLEMON_SHOULDER_RIGHT);
        try {
            return TagParser.parseCompoundFully(data);
        } catch (Exception exception) {
            return new CompoundTag();
        }
    }

    @Override
    public void cobblemon$setShoulderPokemon(boolean isLeft, CompoundTag data) {
        this.entityData.set(isLeft ? COBBLEMON_SHOULDER_LEFT : COBBLEMON_SHOULDER_RIGHT, data.copy().toString());
    }


    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        if (this.getVehicle() instanceof PokemonEntity) {
            return super.getDefaultDimensions(Pose.STANDING);
        }
        return super.getDefaultDimensions(pose);
    }


    private void recallPokemon(UUID uuid) {
        // We need to do this cause the Entity doesn't store a reference to its storage
        final PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(this.getUUID(), this.registryAccess());
        for (Pokemon pokemon : party) {
            if (pokemon.getUuid().equals(uuid)) {
                pokemon.recall();
            }
        }
    }

    @Redirect(
            method = "getDestroySpeed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"
            )
    )
    private boolean cobblemon$considerHoverOnGround(Player player) {
        // If riding a Hover Mount, return true to bypass the 5x slowdown
        if (player.getVehicle() instanceof PokemonEntity vehicle) {

            var ridingController = vehicle.getRidingController();
            if (ridingController == null) player.onGround();

            var activeContext = ridingController.getContext();
            if (activeContext == null) player.onGround();

            if (activeContext.getStyle() == RidingStyle.AIR &&
                activeContext.getBehaviour().equals(HoverBehaviour.Companion.getKEY())) {
                return true;
            }
        }
        // Otherwise, return the actual value
        return player.onGround();
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    public void isInvulnerableTo(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> ci) {
        if (!level().isClientSide() && (Object) this instanceof ServerPlayer player) {
            boolean invulnerableInBattle = player.level().getGameRules().get(CobblemonGameRules.BATTLE_INVULNERABILITY);
            boolean inBattle = Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null;
            if (invulnerableInBattle && inBattle) {
                ci.setReturnValue(true);
            }
        }
    }

    @Override @Nullable
    public PokedexEntityData resolvePokemonScan() {
        if (CompoundTagUtilities.isShoulderPokemon(this.cobblemon$getShoulderPokemon(false))){
            return getDataFromShoulderPokemon(this.cobblemon$getShoulderPokemon(false));
        }
        if (CompoundTagUtilities.isShoulderPokemon(this.cobblemon$getShoulderPokemon(true))){
            return getDataFromShoulderPokemon(this.cobblemon$getShoulderPokemon(true));
        }
        return null;
    }

    @Nullable @Unique
    private PokedexEntityData getDataFromShoulderPokemon(CompoundTag shoulderTag) {
        Pokemon pokemon;
        if (level().isClientSide()) {
            CompoundTag pokemonTag = shoulderTag.getCompoundOrEmpty(DataKeys.POKEMON);
            if (pokemonTag.isEmpty()) return null;
            Identifier speciesIdentifier = Identifier.tryParse(pokemonTag.getStringOr(DataKeys.POKEMON_SPECIES_IDENTIFIER, ""));
            if (speciesIdentifier == null) return null;
            Species species = PokemonSpecies.getByIdentifier(speciesIdentifier);
            if (species == null) return null;
            String formId = pokemonTag.getStringOr(DataKeys.POKEMON_FORM_ID, "");
            FormData form = species.getStandardForm();
            List<FormData> formList = species.getForms().stream().filter(it -> it.formOnlyShowdownId().equals(formId)).toList();
            if (!formList.isEmpty()) form = formList.getFirst();
            if (form == null) return null;
            String genderString = pokemonTag.getStringOr(DataKeys.POKEMON_GENDER, "");
            if (genderString.isEmpty()) return null;
            Gender gender;
            try {
                gender = Gender.valueOf(genderString);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            boolean shiny = pokemonTag.getBooleanOr(DataKeys.POKEMON_SHINY, false);
            int level = pokemonTag.getIntOr(DataKeys.POKEMON_LEVEL, 0);
            Set<String> aspects = shoulderTag.getListOrEmpty(DataKeys.SHOULDER_ASPECTS).stream()
                    .filter(StringTag.class::isInstance)
                    .map(StringTag.class::cast)
                    .map(StringTag::value)
                    .collect(Collectors.toSet());

            pokemon = new Pokemon();
            pokemon.setSpecies(species);
            pokemon.setForm(form);
            pokemon.setGender(gender);
            pokemon.setShiny(shiny);
            pokemon.setLevel(level);
            pokemon.setForcedAspects(aspects);
        } else {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(this.getUUID(), this.registryAccess());
            UUID shoulderPokemonId = shoulderTag.read(DataKeys.SHOULDER_UUID, UUIDUtil.CODEC).orElse(null);
            if (shoulderPokemonId == null) return null;
            pokemon = party.get(shoulderPokemonId);
        }
        return (pokemon == null) ? null : new PokedexEntityData(pokemon, null);
    }

    @Override
    public LivingEntity resolveEntityScan() {
        return this;
    }

    @WrapOperation(
            method = "rideTick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;wantsToStopRiding()Z")
    )
    public boolean delegateDismountToController(Player instance, Operation<Boolean> original) {
        if (this.getVehicle() instanceof PokemonEntity pokemonEntity) {
            if (!original.call(instance)) return false;
            return pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) -> {
                if (!behaviour.canStopRiding(settings, state, pokemonEntity, instance)) return false;
                // Use the vanilla sneak dismount in every riding style. Keep an air
                // mount flying, as the dedicated dismount packet handler does.
                if (pokemonEntity.getRidingController().getContext().getStyle() == RidingStyle.AIR) {
                    pokemonEntity.setFlying(true);
                }
                return true;
            });
        }
        return original.call(instance);
    }

    @Override
    public OrientationController getOrientationController() {
        return cobblemon$orientationController;
    }

    @Override
    public void setDriverInput(Vector3f driverInput) {
        cobblemon$driverInput = driverInput;
    }

    @Override
    public Vector3f getDriverInput() {
        return cobblemon$driverInput;
    }

    @Override
    public void setLastSentDriverInput(Vector3f lastSentDriverInput) {
        cobblemon$lastSentDriverInput = lastSentDriverInput;
    }

    @Override
    public Vector3f getLastSentDriverInput() {
        return cobblemon$lastSentDriverInput;
    }

    @Override
    public float cobblemon$getRideXRot() {
        return this.cobblemon$rideXRot;
    }

    @Override
    public void cobblemon$setRideXRot(float rideXRot) {
        this.cobblemon$rideXRot = Mth.wrapDegrees(rideXRot);
    }

    @Override
    public float cobblemon$getRideYRot() {
        return this.cobblemon$rideYRot;
    }

    @Override
    public void cobblemon$setRideYRot(float rideYRot) {
        this.cobblemon$rideYRot = Mth.wrapDegrees(rideYRot);
    }

    @Override
    public Vec3 cobblemon$getRideEyePos() {
        return this.cobblemon$rideEyePos;
    }

    @Override
    public void cobblemon$setRideEyePos(Vec3 rideEyePos) {
        this.cobblemon$rideEyePos = rideEyePos;
    }

}
