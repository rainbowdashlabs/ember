/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.FormRefusal;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.form.service.PublicFormRateLimiter;
import dev.chojo.ember.feature.form.service.PublicFormService;
import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The station behind a form's link, and a form refused at its public address, over HTTP.
 */
class PublicFormAddressRoutesTest {
    @Test
    void aLinkNamesItsStationAndARefusedAddressSaysWhy() {
        var publicForms = mock(PublicFormService.class);
        var form = mock(Form.class);
        var station = mock(Station.class);
        when(station.uid()).thenReturn(UUID.randomUUID());
        when(station.name()).thenReturn("Wache");
        when(publicForms.sharedForm("t")).thenReturn(form);
        when(publicForms.stationOf(form)).thenReturn(station);
        when(publicForms.resolveFormOfStation(eq("wache"), any()))
                .thenThrow(FormRefusal.FORM_NOT_OPENLY_ADDRESSED.raise());
        var harness = RouteHarness.serving(new PublicFormRoutes(
                mock(FormService.class),
                publicForms,
                mock(SubmitterHashService.class),
                mock(PublicFormRateLimiter.class),
                mock(ConsentService.class),
                new Network(),
                mock(StationLogoService.class)));

        harness.run((server, client) -> {
            assertEquals(
                    "Wache",
                    json(client.get(PREFIX + "/public/shared-form/t/brand"))
                            .path("name")
                            .asString());
            assertEquals(
                    FormRefusal.FORM_NOT_OPENLY_ADDRESSED,
                    refusalOf(client.get(PREFIX + "/public/wache/forms/" + UUID.randomUUID())));
        });
    }
}
