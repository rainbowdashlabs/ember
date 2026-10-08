/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityInfo;
import dev.chojo.ember.feature.signing.service.PublishedCertificates;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a station tells the open web about itself, and whether it answers the open web at all.
 *
 * <p>A station answers when it publishes anything: its wiki, its calendar, a listed page, a
 * waiting list, its blog, or a form anybody can reach by its link. A station with none of these is
 * refused exactly like one that does not exist, so a stranger cannot count stations. An
 * association's own station answers with its wiki alone, and only when the association has put that
 * wiki on the public web.
 */
@Singleton
public class PublicStationInfoService {
    private final StationRepository stations;
    private final StationLogoService logoService;
    private final PageService pageService;
    private final WaitingListService waitingListService;
    private final NewsService newsService;
    private final FormService formService;
    private final PublishedCertificates sealCertificates;

    @Inject
    public PublicStationInfoService(
            StationRepository stations,
            StationLogoService logoService,
            PageService pageService,
            WaitingListService waitingListService,
            NewsService newsService,
            FormService formService,
            PublishedCertificates sealCertificates) {
        this.stations = stations;
        this.logoService = logoService;
        this.pageService = pageService;
        this.waitingListService = waitingListService;
        this.newsService = newsService;
        this.formService = formService;
        this.sealCertificates = sealCertificates;
    }

    /**
     * The public card of the station a public address names, by uid or by its readable name.
     *
     * @param address the station's uid or public slug, as it stands in the address
     * @return what the station publishes about itself
     */
    public PublicStationInfo info(String address) {
        var station = stations.findByAddress(address).orElseThrow(StationRefusal.PUBLIC_STATION_NOTHING_TO_SHOW::raise);
        var offer = offer(station);
        boolean reachableByForms =
                station.stationKind() != StationKind.CLUSTER_HOME && formService.hasOpenlyAddressedForms(station.id());
        if (offer.isEmpty() && !reachableByForms) {
            throw StationRefusal.PUBLIC_STATION_NOTHING_TO_SHOW.raise();
        }
        String landingPageSlug =
                offer.pages() ? pageService.getLandingPageSlug(station.id()).orElse(null) : null;
        return publicInfo(station, offer, landingPageSlug);
    }

    /**
     * What of the station is on the public web. An association's own station offers its wiki at most.
     * A form reached only by its link is no part of it, since the public page would show nothing to go
     * to; a link to that page leads somewhere exactly when the offer is not empty.
     *
     * @param station the station
     * @return each public part and whether the station has it
     */
    public PublicOffer offer(Station station) {
        return Objects.requireNonNull(offers(List.of(station)).get(station.id()), "every station given has an offer");
    }

    /**
     * What of each station is on the public web, the same as {@link #offer(Station)} answers for one,
     * with one query per kind of content for all of them together. A station is only asked about a kind
     * it has switched on.
     *
     * @param stations the stations
     * @return each station's offer by station id
     */
    public Map<Integer, PublicOffer> offers(Collection<Station> stations) {
        var regular = stations.stream()
                .filter(station -> station.stationKind() != StationKind.CLUSTER_HOME)
                .toList();
        Set<Integer> pages = pageService.withListedPages(idsWhere(regular, Station::publicPagesEnabled));
        Set<Integer> waitlists =
                waitingListService.withPublicWaitlists(idsWhere(regular, Station::publicWaitlistEnabled));
        Set<Integer> blogs = newsService.withPublicBlogEntries(idsWhere(regular, Station::publicBlogEnabled));
        Map<Integer, PublicOffer> offers = new HashMap<>();
        for (var station : stations) {
            boolean hasPublicKb = station.publicKbMode() != PublicKbMode.OFF;
            offers.put(
                    station.id(),
                    station.stationKind() == StationKind.CLUSTER_HOME
                            ? new PublicOffer(hasPublicKb, false, false, false, false)
                            : new PublicOffer(
                                    hasPublicKb,
                                    station.publicCalendarEnabled(),
                                    pages.contains(station.id()),
                                    waitlists.contains(station.id()),
                                    blogs.contains(station.id())));
        }
        return offers;
    }

    private static List<Integer> idsWhere(List<Station> stations, Predicate<Station> switchedOn) {
        return stations.stream().filter(switchedOn).map(Station::id).toList();
    }

    private PublicStationInfo publicInfo(Station station, PublicOffer offer, @Nullable String landingPageSlug) {
        return new PublicStationInfo(
                station.uid().toString(),
                station.name(),
                station.discoveryDescription(),
                logoService.exists(station.id()),
                offer.knowledgeBase(),
                offer.calendar(),
                offer.pages(),
                offer.waitlist(),
                offer.blog(),
                landingPageSlug,
                station.publicSlug(),
                station.defaultTheme(),
                station.defaultFeel() != null ? station.defaultFeel().name() : null,
                station.customThemeColors(),
                StationFormat.timezoneNameOf(station),
                sealCertificates.authoritiesOfStation(station.id()));
    }

    /**
     * What a station tells the open web about itself.
     *
     * <p>The timezone is here because a public page is written twice, once by the server and once by
     * the browser, and neither of those two machines is where the reader is. A date put on whichever
     * clock happened to write it comes out differently in the two copies. The station's own clock is
     * the one answer both can agree on, and the honest one besides: an appointment at seven at the
     * station is at seven whoever is reading about it.
     *
     * <p>The seal authorities are the installation's signing authorities that issued the station's
     * seal certificates, so a reader can compare the fingerprint in a sealed document with the one
     * published here. Empty while the station has never sealed anything; asking never creates a key.
     */
    public record PublicStationInfo(
            String stationUid,
            String name,
            @Nullable String description,
            boolean hasLogo,
            boolean hasPublicKb,
            boolean hasPublicCalendar,
            boolean hasPublicPages,
            boolean hasPublicWaitlist,
            boolean hasPublicBlog,
            @Nullable String landingPageSlug,
            @Nullable String publicSlug,
            String defaultTheme,
            @Nullable String defaultFeel,
            @Nullable String customThemeColors,
            String timezone,
            List<SigningAuthorityInfo> sealAuthorities) {}
}
