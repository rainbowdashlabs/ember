/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.util.PdfText;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A run of one template for many members reads what is the same for all of them once, and what each of
 * them needs for all of them at once, and draws the same documents as one member at a time would.
 */
class GenerationBatchTest extends GeneratorTestBase {

    @Test
    void aRunReadsTheStationAndTheMembersOnce() {
        var stations = spy(stationRepo);
        var members = spy(stationMemberRepo);
        var wiring = wire(stationRepo.create("Zähl Wache"), stations, members);
        var manager = wiring.member("batch-manager@test.com", "Nora", "Fülling");
        var anna = wiring.member("batch-anna@test.com", "Anna", "Arndt");
        var ben = wiring.member("batch-ben@test.com", "Ben", "Brandt");
        var carla = wiring.member("batch-carla@test.com", "Carla", "Conrad");
        stationMemberRepo.addManager(manager.id(), anna.id());
        int school = profileFieldRepo
                .create(wiring.station().id(), "Schule", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldRepo.assignToRole(school, ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(anna.id(), school, StringNode.valueOf("Grundschule am See"));
        profileFieldRepo.setValue(ben.id(), school, StringNode.valueOf("Gymnasium"));
        var request = letter("Zählung")
                .body(List.of(row(
                        text("{{member.fullName}} von {{station.name}} besucht die {{profile.%d}}, ".formatted(school)
                                + "vertreten durch {{guardian1.fullName}}."))))
                .build();
        int templateId =
                wiring.templates().create(wiring.owner(), request, manager.id()).id();
        var template = new DocumentTemplateRepository().findById(templateId).orElseThrow();
        var memberIds = List.of(anna.id(), ben.id(), carla.id());
        clearInvocations(stations, members);

        var batch = wiring.generator()
                .batch(
                        wiring.generator().sourceOf(template),
                        GenerationContext.by(manager.id(), DocumentIssuer.NONE),
                        memberIds);
        var texts = new ArrayList<String>();
        var missing = new ArrayList<List<String>>();
        for (int memberId : memberIds) {
            var prepared = batch.prepare(memberId);
            missing.add(prepared.missing().stream().map(MissingValue::key).toList());
            texts.add(Objects.requireNonNull(
                    PdfText.extract(batch.render(prepared).pdf())));
        }

        verify(stations, times(1)).findById(wiring.station().id());
        verify(members, times(1)).findByIds(any());
        verify(members, times(1)).findManagersOf(any());
        verify(members, never()).findById(anyInt());
        verify(members, never()).findManagers(anyInt());
        assertTrue(texts.get(0).contains("Grundschule am See"), texts.get(0));
        assertTrue(texts.get(0).contains("Zähl Wache"), texts.get(0));
        assertTrue(texts.get(1).contains("Gymnasium"), texts.get(1));
        assertEquals(List.of(), missing.get(0));
        assertEquals(List.of("guardian1.fullName"), missing.get(1));
        assertEquals(List.of("profile." + school, "guardian1.fullName"), missing.get(2));
    }
}
