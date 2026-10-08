/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Granting stations the instance's mail providers, one at a time and many at once.
 */
class InstanceMailGrantServiceTest extends RepositoryTestBase {
    private final InstanceMailGrantRepository grants = new InstanceMailGrantRepository();
    private InstanceMailGrantService service;
    private Station nord;
    private Station sued;

    @BeforeEach
    void setup() {
        service = new InstanceMailGrantService(grants, stationRepo);
        nord = stationRepo.create("Grant Nord");
        sued = stationRepo.create("Grant Sued");
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(nord.id());
        stationRepo.delete(sued.id());
    }

    @Test
    void aStationIsGrantedWithALimitAndWithdrawnAgain() {
        var granted = service.grant(nord.uid(), 25);

        assertTrue(granted.granted());
        assertEquals(25, granted.dailyLimit());
        assertEquals("Grant Nord", granted.name());
        assertEquals(0, granted.sentToday());
        assertTrue(service.forStation(nord.id()).granted(), "the station sees it too");

        var withdrawn = service.withdraw(nord.uid());

        assertFalse(withdrawn.granted());
        assertNull(withdrawn.grantedAt());
        assertNull(withdrawn.dailyLimit());
    }

    @Test
    void severalStationsAreGrantedAndWithdrawnAtOnce() {
        var listed = service.grantAll(List.of(nord.uid(), sued.uid()), null);

        assertTrue(listed.stream()
                .filter(station -> station.stationUid().equals(nord.uid())
                        || station.stationUid().equals(sued.uid()))
                .allMatch(station -> station.granted() && station.dailyLimit() == null));

        service.withdrawAll(List.of(nord.uid()));

        assertFalse(service.station(nord.uid()).granted());
        assertTrue(service.station(sued.uid()).granted());
    }

    @Test
    void aLimitBelowOneIsRefusedAndNothingIsWritten() {
        var refused = assertThrows(RefusalResponse.class, () -> service.grantAll(List.of(nord.uid()), 0));

        assertEquals(SystemRefusal.INSTANCE_MAIL_LIMIT_NOT_POSITIVE, refused.refusal());
        assertFalse(service.station(nord.uid()).granted());
    }

    @Test
    void anUnknownStationIsRefusedBeforeAnyOtherIsGranted() {
        var unknown = UUID.fromString("00000000-0000-0000-0000-00000000dead");

        var refused = assertThrows(RefusalResponse.class, () -> service.grantAll(List.of(nord.uid(), unknown), null));

        assertEquals(SystemRefusal.STATION_NOT_HERE_FOR_INSTANCE_MAIL, refused.refusal());
        assertFalse(service.station(nord.uid()).granted(), "nothing was written");
        assertEquals(
                SystemRefusal.STATION_NOT_HERE_FOR_INSTANCE_MAIL,
                assertThrows(RefusalResponse.class, () -> service.forStation(Integer.MAX_VALUE))
                        .refusal());
    }
}
