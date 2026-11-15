package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.net.messages.client.data.FormEvolutionOverride;
import com.cobblemon.mod.common.net.messages.client.data.SpeciesEvolutionEntry;
import com.cobblemon.mod.common.net.messages.client.data.SpeciesEvolutionSyncPacket;
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution;
import com.cobblemon.mod.common.util.adapters.RegistryAccessResolver;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Packet contract checks; no registry mutation and no claim to exercise a remote connection. */
public final class CobblemonEvolutionSyncGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void allSpeciesEvolutionEntriesPreserveFramingAndItemPredicates(GameTestHelper context) {
        var entries = new ArrayList<SpeciesEvolutionEntry>();
        for (var species : PokemonSpecies.getSpecies()) {
            // Resolved form values exercise the public override codec, including empty sets.
            var forms = species.getForms().stream().map(form -> new FormEvolutionOverride(
                    form.getName(), form.getEvolutions(), form.getPreEvolution())).toList();
            entries.add(new SpeciesEvolutionEntry(species.getResourceIdentifier(),
                    species.getEvolutions(), species.getPreEvolution(), forms));
        }
        if (entries.size() < 1000) throw new AssertionError("Evolution sync fixture did not load all species");
        assertExpectedWhiteStripedPreEvolution(entries);
        var packet = new SpeciesEvolutionSyncPacket(entries);
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
        SpeciesEvolutionSyncPacket received = null;
        try {
            packet.encode(wire);
            received = SpeciesEvolutionSyncPacket.Companion.decode(wire);
            if (wire.isReadable()) throw new AssertionError("Evolution packet left bytes outside its frame");
            var payload = received.getBuffer();
            if (payload == null || payload.readVarInt() != entries.size()) {
                throw new AssertionError("Evolution packet lost its collection prefix");
            }
            boolean checkedStone = false;
            var preEvolutionDifferences = new ArrayList<String>();
            var decodedEntries = new ArrayList<SpeciesEvolutionEntry>(entries.size());
            for (var expected : entries) {
                var actual = received.decodeEntry(payload);
                if (actual == null || !expected.getSpeciesIdentifier().equals(actual.getSpeciesIdentifier())) {
                    throw new AssertionError("Evolution packet dropped or misaligned " + expected.getSpeciesIdentifier());
                }
                decodedEntries.add(actual);
                assertEvolutionIds(expected.getEvolutions(), actual.getEvolutions(), expected.getSpeciesIdentifier().toString());
                checkPreEvolution(expected.getPreEvolution(), actual.getPreEvolution(), expected.getSpeciesIdentifier().toString(), preEvolutionDifferences);
                if (expected.getFormOverrides().size() != actual.getFormOverrides().size()) {
                    throw new AssertionError("Evolution packet lost form overrides for " + expected.getSpeciesIdentifier());
                }
                for (int i = 0; i < expected.getFormOverrides().size(); i++) {
                    var before = expected.getFormOverrides().get(i);
                    var after = actual.getFormOverrides().get(i);
                    if (!before.getFormName().equals(after.getFormName())) throw new AssertionError("Form override order changed");
                    assertEvolutionIds(before.getEvolutions(), after.getEvolutions(), before.getFormName());
                    checkPreEvolution(before.getPreEvolution(), after.getPreEvolution(), expected.getSpeciesIdentifier() + "/" + before.getFormName(), preEvolutionDifferences);
                }
                if (actual.getSpeciesIdentifier().equals(Identifier.parse("cobblemon:eevee"))) {
                    var jolteon = actual.getEvolutions().stream().filter(e -> e.getId().equals("eevee_jolteon"))
                            .findFirst().orElseThrow(() -> new AssertionError("Eevee lost Jolteon evolution"));
                    if (!(jolteon instanceof ItemInteractionEvolution stone)
                            || !stone.getRequiredContext().test(new ItemStack(CobblemonItems.THUNDER_STONE))
                            || stone.getRequiredContext().test(new ItemStack(CobblemonItems.FIRE_STONE))) {
                        throw new AssertionError("Decoded Jolteon predicate does not distinguish thunder and fire stones");
                    }
                    checkedStone = true;
                }
            }
            if (!checkedStone || payload.isReadable()) throw new AssertionError("Incomplete evolution payload verification");
            if (!preEvolutionDifferences.isEmpty()) {
                throw new AssertionError("Evolution sync changed pre-evolution references: " + preEvolutionDifferences);
            }

            // Synchronizing the first pass onto resolved species leaves ordinary mutable sets in live species.
            // Re-encode that server-side shape explicitly: the registered adapter must accept Set implementations,
            // while deserialization may still create LazySet instances to preserve lazy species loading.
            var resolvedEntries = decodedEntries.stream().map(entry -> new SpeciesEvolutionEntry(
                    entry.getSpeciesIdentifier(),
                    new HashSet<>(entry.getEvolutions()),
                    entry.getPreEvolution(),
                    entry.getFormOverrides().stream().map(override -> new FormEvolutionOverride(
                            override.getFormName(),
                            override.getEvolutions() == null ? null : new HashSet<>(override.getEvolutions()),
                            override.getPreEvolution())).toList())).toList();
            var resolvedPacket = new SpeciesEvolutionSyncPacket(resolvedEntries);
            var resolvedWire = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
            SpeciesEvolutionSyncPacket resolvedReceived = null;
            try {
                resolvedPacket.encode(resolvedWire);
                resolvedReceived = SpeciesEvolutionSyncPacket.Companion.decode(resolvedWire);
                if (resolvedWire.isReadable()) throw new AssertionError("Resolved evolution packet left bytes outside its frame");
                var resolvedPayload = resolvedReceived.getBuffer();
                if (resolvedPayload == null || resolvedPayload.readVarInt() != resolvedEntries.size()) {
                    throw new AssertionError("Resolved evolution re-encode lost its collection prefix");
                }
                var resolvedPreEvolutionDifferences = new ArrayList<String>();
                for (var expected : resolvedEntries) {
                    var actual = resolvedReceived.decodeEntry(resolvedPayload);
                    if (actual == null || !expected.getSpeciesIdentifier().equals(actual.getSpeciesIdentifier())) {
                        throw new AssertionError("Resolved evolution re-encode dropped or misaligned " + expected.getSpeciesIdentifier());
                    }
                    assertEvolutionIds(expected.getEvolutions(), actual.getEvolutions(), expected.getSpeciesIdentifier().toString());
                    checkPreEvolution(expected.getPreEvolution(), actual.getPreEvolution(),
                            expected.getSpeciesIdentifier().toString(), resolvedPreEvolutionDifferences);
                    if (expected.getFormOverrides().size() != actual.getFormOverrides().size()) {
                        throw new AssertionError("Resolved evolution re-encode lost form overrides for " + expected.getSpeciesIdentifier());
                    }
                    for (int i = 0; i < expected.getFormOverrides().size(); i++) {
                        var before = expected.getFormOverrides().get(i);
                        var after = actual.getFormOverrides().get(i);
                        if (!before.getFormName().equals(after.getFormName())) {
                            throw new AssertionError("Resolved evolution re-encode changed form override order");
                        }
                        assertEvolutionIds(before.getEvolutions(), after.getEvolutions(), before.getFormName());
                        checkPreEvolution(before.getPreEvolution(), after.getPreEvolution(),
                                expected.getSpeciesIdentifier() + "/" + before.getFormName(), resolvedPreEvolutionDifferences);
                    }
                }
                if (!resolvedPreEvolutionDifferences.isEmpty()) {
                    throw new AssertionError("Resolved evolution re-encode changed pre-evolution references: "
                            + resolvedPreEvolutionDifferences);
                }
                if (resolvedPayload.isReadable()) {
                    throw new AssertionError("Resolved evolution re-encode left unread species entries");
                }
            } finally {
                if (resolvedReceived != null && resolvedReceived.getBuffer() != null) resolvedReceived.getBuffer().release();
                resolvedWire.release();
            }
            System.out.println("[Cobblemon GameTest] Evolution sync decoded " + entries.size()
                    + " species and form overrides, then re-encoded resolved HashSets without dropped entries or remaining bytes; stone predicate preserved");
        } finally {
            if (received != null && received.getBuffer() != null) received.getBuffer().release();
            wire.release();
        }
        context.succeed();
    }

    @GameTest
    public void partyNbtReloadPreservesPokemonIdentityFormMovesExperienceAndHealth(GameTestHelper context) {
        var pokemon = PokemonProperties.Companion.parse("basculin striped=white level=31").create();
        pokemon.setCurrentHealth(Math.max(1, pokemon.getMaxHealth() - 7));
        pokemon.getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("tackle").create());
        pokemon.getMoveSet().setMove(1, Moves.INSTANCE.getByNameOrDummy("watergun").create());

        UUID pokemonId = pokemon.getUuid();
        Identifier speciesId = pokemon.getSpecies().getResourceIdentifier();
        String formId = pokemon.getForm().formOnlyShowdownId();
        int experience = pokemon.getExperience();
        int currentHealth = pokemon.getCurrentHealth();
        var moveIds = pokemon.getMoveSet().getMoves().stream()
                .map(move -> move.getTemplate().getName()).toList();
        if (!"cobblemon:basculin".equals(speciesId.toString()) || !"whitestriped".equals(formId)) {
            throw new AssertionError("Persistence fixture did not resolve Basculin's White-Striped form: "
                    + speciesId + "/" + formId);
        }

        var registryAccess = context.getLevel().registryAccess();
        var party = new PartyStore(UUID.randomUUID());
        if (!party.add(pokemon)) throw new AssertionError("Could not add the Pokémon to the persistence fixture party");
        var savedParty = party.saveToNBT(new CompoundTag(), registryAccess);
        var restoredParty = new PartyStore(party.getUuid()).loadFromNBT(savedParty, registryAccess);
        restoredParty.initialize();

        var restored = restoredParty.get(0);
        if (restored == null
                || !restored.getUuid().equals(pokemonId)
                || !restored.getSpecies().getResourceIdentifier().equals(speciesId)
                || !restored.getForm().formOnlyShowdownId().equals(formId)
                || restored.getExperience() != experience
                || restored.getCurrentHealth() != currentHealth
                || !restored.getMoveSet().getMoves().stream().map(move -> move.getTemplate().getName()).toList().equals(moveIds)) {
            throw new AssertionError("Party NBT reload changed Pokémon UUID, species/form, moves, experience, or health");
        }
        context.succeed();
    }

    @GameTest
    public void registryScopeRestoresOuterAccessAfterFailure(GameTestHelper context) {
        var resolver = RegistryAccessResolver.INSTANCE;
        var before = resolver.getAccess();
        var worldAccess = context.getLevel().registryAccess();
        resolver.withAccess(worldAccess, () -> {
            if (resolver.getAccess() != worldAccess) throw new AssertionError("Packet registry scope not applied");
            var expectedFailure = new IllegalStateException("deliberate nested registry scope failure");
            try {
                resolver.withAccess(RegistryAccess.EMPTY, () -> { throw expectedFailure; });
                throw new AssertionError("Registry scope swallowed an exception");
            } catch (IllegalStateException actual) {
                if (actual != expectedFailure) throw actual;
            }
            if (resolver.getAccess() != worldAccess) throw new AssertionError("Nested failure leaked registry scope");
            return null;
        });
        if (resolver.getAccess() != before) throw new AssertionError("Registry scope escaped packet operation");
        context.succeed();
    }

    private static void assertEvolutionIds(Set<? extends com.cobblemon.mod.common.api.pokemon.evolution.Evolution> before,
                                          Set<? extends com.cobblemon.mod.common.api.pokemon.evolution.Evolution> after,
                                          String name) {
        if (before == null || after == null) {
            if (before != after) throw new AssertionError("Evolution null/empty distinction lost: " + name);
            return;
        }
        var expected = before.stream().map(e -> e.getId()).collect(Collectors.toSet());
        var actual = after.stream().map(e -> e.getId()).collect(Collectors.toSet());
        if (!expected.equals(actual)) throw new AssertionError("Evolution IDs changed: " + name);
    }

    private static void checkPreEvolution(com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution before,
                                           com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution after,
                                           String name, ArrayList<String> differences) {
        if (before == null || after == null) {
            if (before != after) differences.add(name + ": null state changed");
        } else if (!before.getSpecies().getResourceIdentifier().equals(after.getSpecies().getResourceIdentifier())
                || !before.getForm().formOnlyShowdownId().equals(after.getForm().formOnlyShowdownId())) {
            differences.add(name + ": " + before.getSpecies().getResourceIdentifier() + "/" + before.getForm().getName()
                    + " -> " + after.getSpecies().getResourceIdentifier() + "/" + after.getForm().getName());
        }
    }

    private static void assertExpectedWhiteStripedPreEvolution(ArrayList<SpeciesEvolutionEntry> entries) {
        var basculegion = entries.stream()
                .filter(entry -> entry.getSpeciesIdentifier().equals(Identifier.parse("cobblemon:basculegion")))
                .findFirst().orElseThrow(() -> new AssertionError("Basculegion evolution fixture is missing"));
        assertWhiteStriped(basculegion.getPreEvolution(), "cobblemon:basculegion");
        var female = basculegion.getFormOverrides().stream()
                .filter(override -> override.getFormName().equals("F"))
                .findFirst().orElseThrow(() -> new AssertionError("Basculegion F form override is missing"));
        assertWhiteStriped(female.getPreEvolution(), "cobblemon:basculegion/F");
    }

    private static void assertWhiteStriped(com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution preEvolution,
                                           String source) {
        if (preEvolution == null
                || !preEvolution.getSpecies().getResourceIdentifier().equals(Identifier.parse("cobblemon:basculin"))
                || !preEvolution.getForm().formOnlyShowdownId().equals("whitestriped")) {
            throw new AssertionError(source + " must resolve to cobblemon:basculin/whitestriped, got "
                    + (preEvolution == null ? "null" : preEvolution.getSpecies().getResourceIdentifier() + "/"
                    + preEvolution.getForm().formOnlyShowdownId()));
        }
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}
