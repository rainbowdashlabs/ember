/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * The forms somebody without an account reaches: by the link a station handed out, or by the public
 * address a page or the station's site shows.
 */
@Singleton
public class PublicFormService {
    private final FormService forms;
    private final StationRepository stations;

    @Inject
    public PublicFormService(FormService forms, StationRepository stations) {
        this.forms = forms;
        this.stations = stations;
    }

    /**
     * The form behind a link.
     *
     * @param token the token the link carries
     */
    public Form sharedForm(String token) {
        return forms.findByShareToken(token).orElseThrow(Refusal.FORM_LINK_UNKNOWN::raise);
    }

    /**
     * The station a form behind a link belongs to, whose name and colours frame it.
     */
    public Station stationOf(Form form) {
        return stations.findById(form.stationId()).orElseThrow(Refusal.STATION_NOT_HERE_BEHIND_FORM_LINK::raise);
    }

    /**
     * The form a public address names, when it may be answered from outside at all.
     *
     * <p>The station part of the address is whatever the link was built from, its uid or its
     * readable name, the same as every other public address of that station. A form of another
     * station under that address is not here.
     *
     * @param stationAddress the station as the address names it
     * @param formUid        the form's public identity
     */
    public Form resolveFormOfStation(String stationAddress, UUID formUid) {
        var station =
                stations.findByAddress(stationAddress).orElseThrow(Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_FORM::raise);
        var form = forms.findByPublicUid(formUid).orElseThrow(Refusal.PUBLIC_FORM_NOT_HERE::raise);
        if (form.stationId() != station.id()) {
            throw Refusal.PUBLIC_FORM_NOT_HERE.raise();
        }
        if (form.purpose() != FormPurpose.CONTACT && form.purpose() != FormPurpose.POLL) {
            throw Refusal.FORM_NOT_ANSWERED_FROM_OUTSIDE.raise();
        }
        if (!form.visibility().openlyAddressed()) {
            throw Refusal.FORM_NOT_OPENLY_ADDRESSED.raise();
        }
        return form;
    }
}
