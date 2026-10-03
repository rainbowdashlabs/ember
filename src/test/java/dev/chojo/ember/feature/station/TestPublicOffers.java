/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station;

import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Stubs what a mocked {@link PublicStationInfoService} says a station offers, for one station and for
 * many at once alike.
 */
public final class TestPublicOffers {
    private TestPublicOffers() {}

    /**
     * Answers every question about offers with the given rule.
     *
     * @param publicInfo the mock
     * @param offerOf    what each station offers
     */
    public static void stub(PublicStationInfoService publicInfo, Function<Station, PublicOffer> offerOf) {
        when(publicInfo.offer(any())).thenAnswer(invocation -> offerOf.apply(invocation.getArgument(0)));
        when(publicInfo.offers(any())).thenAnswer(invocation -> {
            Collection<Station> stations = invocation.getArgument(0);
            Map<Integer, PublicOffer> offers = new HashMap<>();
            stations.forEach(station -> offers.put(station.id(), offerOf.apply(station)));
            return offers;
        });
    }

    /**
     * Answers every question about offers with the same offer.
     *
     * @param publicInfo the mock
     * @param offer      what every station offers
     */
    public static void stub(PublicStationInfoService publicInfo, PublicOffer offer) {
        stub(publicInfo, station -> offer);
    }
}
