/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.LegalRefusal;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.form.service.PublicFormRateLimiter;
import dev.chojo.ember.feature.form.service.PublicFormService;
import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An answer to a public form, sent against legal documents that changed while the form stood open,
 * is a conflict with what the reader agreed to and carries its own code. The public form page tells
 * a second answer apart from it by that code, since both answer with the same status.
 */
class PublicFormConsentRouteTest extends RepositoryTestBase {
    private static FormService formService;
    private static ConsentService consent;
    private static RouteHarness harness;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setupClass() {
        formService = new FormService(
                formRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class),
                restrictionService,
                new DomainEventBus(Set.of()));
        consent = mock(ConsentService.class);
        var hashes = mock(SubmitterHashService.class);
        when(hashes.hash(any(), anyInt())).thenReturn(new byte[] {1});
        station = stationRepo.create("PublicFormConsentStation");
        account = accountRepo.create("public-consent@test.com", "Conny", "Consent");
        member = stationMemberRepo.create(station.id(), account.id());
        harness = RouteHarness.serving(new PublicFormRoutes(
                        formService,
                        new PublicFormService(formService, stationRepo),
                        hashes,
                        mock(PublicFormRateLimiter.class),
                        consent,
                        new Network(),
                        mock(StationLogoService.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void anAnswerAgainstChangedLegalDocumentsIsAConflictOfItsOwn() {
        when(consent.requireAcceptance(any(), anyString(), anyString(), anyString()))
                .thenThrow(LegalRefusal.LEGAL_DOCUMENTS_CHANGED.raise());
        int form = formService
                .create(station.id(), "Kontakt", "", false, true, false, null, null, member.id(), FormPurpose.CONTACT)
                .id();
        formService.publish(form);
        String token = formService.replaceShareLink(form, null).orElseThrow();

        var answer = harness.request(
                client -> client.post(PREFIX + "/public/shared-form/" + token + "/responses", body("""
                        {"answers": {}, "consentVersion": "old", "privacyVersion": "old", "tosVersion": "old"}""")));

        assertEquals(409, answer.code());
        assertEquals(LegalRefusal.LEGAL_DOCUMENTS_CHANGED, refusalOf(answer));
    }
}
