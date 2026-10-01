/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.refusal.FormRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** A form can say something of its own once it is sent, with a link that is safe to put before anybody. */
class FormCompletionTest extends RepositoryTestBase {
    private static FormService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new FormService(
                formRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class),
                restrictionService,
                new DomainEventBus(Set.of()));
        station = stationRepo.create("FormCompletionStation");
        account = accountRepo.create("form-completion@test.com", "Form", "Done");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void storesTheMessageAndTheLinkAndCopiesThem() {
        int form = newForm();

        service.setCompletion(form, " Bis Samstag! ", "/public/fw/events/1", "Zur Veranstaltung");
        var copy = service.duplicate(form, "Kopie", member.id()).orElseThrow();

        var stored = service.findById(form).orElseThrow();
        assertEquals("Bis Samstag!", stored.completionMessage());
        assertEquals("/public/fw/events/1", stored.completionLink());
        assertEquals("Zur Veranstaltung", stored.completionLinkLabel());
        assertEquals("Bis Samstag!", copy.completionMessage());
    }

    @Test
    void blankPartsKeepTheGeneralThanks() {
        int form = newForm();

        service.setCompletion(form, "  ", null, "");

        var stored = service.findById(form).orElseThrow();
        assertNull(stored.completionMessage());
        assertNull(stored.completionLink());
        assertNull(stored.completionLinkLabel());
    }

    @Test
    void onlyAWebAddressOrAnAddressHereIsOffered() {
        int form = newForm();

        service.setCompletion(form, null, "HTTPS://example.org", null);
        service.setCompletion(form, null, "http://example.org", null);
        for (var link : new String[] {"javascript:alert(1)", "//evil.example", "example.org"}) {
            var refused = assertThrows(RefusalResponse.class, () -> service.setCompletion(form, null, link, null));
            assertEquals(FormRefusal.FORM_COMPLETION_LINK_NOT_A_LINK, refused.refusal());
        }
    }

    private static int newForm() {
        return service.create(station.id(), "Fertig", "", false, true, false, null, null, member.id(), FormPurpose.POLL)
                .id();
    }
}
