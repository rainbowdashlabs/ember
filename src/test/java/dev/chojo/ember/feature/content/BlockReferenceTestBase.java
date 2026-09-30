/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A station with one news entry and one appointment of every kind a news or event block has to tell
 * apart, and a second station owning one of each.
 *
 * <p>A page may name only what is public: the news entry on the public blog and the appointment on
 * the public calendar. A news or wiki article may name what every member may see: those two, and the
 * internal entry and appointment kept to nobody in particular. Everything else is named by neither:
 * the draft, whatever is kept to a group, a user type or a tag, and whatever another station owns.
 */
public abstract class BlockReferenceTestBase extends RepositoryTestBase {
    protected static Station station;
    protected static Station otherStation;
    protected static Account account;
    protected static StationMember member;

    protected static News publicNews;
    protected static News internalNews;
    protected static News draftNews;
    protected static News groupNews;
    protected static News typeNews;
    protected static News tagNews;
    protected static News elsewhereNews;

    protected static StationEvent publicEvent;
    protected static StationEvent internalEvent;
    protected static StationEvent groupEvent;
    protected static StationEvent typeEvent;
    protected static StationEvent tagEvent;
    protected static StationEvent elsewhereEvent;

    @BeforeAll
    static void createBlockReferences() {
        station = stationRepo.create("Block Reference Station");
        otherStation = stationRepo.create("Block Reference Elsewhere");
        stationRepo.updatePublicBlogEnabled(station.id(), true);
        stationRepo.updatePublicBlogEnabled(otherStation.id(), true);
        account = accountRepo.create("block-reference-" + UUID.randomUUID() + "@test.com", "Bea", "Block");
        member = stationMemberRepo.create(station.id(), account.id());
        var group = memberGroupRepo.create(station.id(), "Vorstand");
        var tag = userTagRepo.create(station.id(), "Atemschutz");

        publicNews = news(station, "Öffentliche Drehleiter", true, "2026-01-10T10:00:00Z");
        internalNews = news(station, "Interne Drehleiter", false, "2026-02-10T10:00:00Z");
        draftNews = news(station, "Entwurf Drehleiter", true, null);
        groupNews = news(station, "Gruppen Drehleiter", true, "2026-03-10T10:00:00Z");
        typeNews = news(station, "Typ Drehleiter", true, "2026-03-11T10:00:00Z");
        tagNews = news(station, "Tag Drehleiter", true, "2026-03-12T10:00:00Z");
        elsewhereNews = news(otherStation, "Fremde Drehleiter", true, "2026-03-13T10:00:00Z");
        restrict(RestrictionType.NEWS, groupNews.id(), onlyGroup(group.id()));
        restrict(RestrictionType.NEWS, typeNews.id(), onlyType());
        restrict(RestrictionType.NEWS, tagNews.id(), onlyTag(tag.id()));

        publicEvent = event(station, "Öffentliche Übung", true);
        internalEvent = event(station, "Interne Übung", false);
        groupEvent = event(station, "Gruppen Übung", true);
        typeEvent = event(station, "Typ Übung", true);
        tagEvent = event(station, "Tag Übung", true);
        elsewhereEvent = event(otherStation, "Fremde Übung", true);
        restrict(RestrictionType.EVENT_VIEW, groupEvent.id(), onlyGroup(group.id()));
        restrict(RestrictionType.EVENT_VIEW, typeEvent.id(), onlyType());
        restrict(RestrictionType.EVENT_VIEW, tagEvent.id(), onlyTag(tag.id()));
    }

    @AfterAll
    static void deleteBlockReferences() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    /** The news entries neither a page nor an article may name, by what makes them so. */
    protected static Map<String, News> newsNobodyMayName() {
        var withheld = new LinkedHashMap<String, News>();
        withheld.put("draft", draftNews);
        withheld.put("kept to a group", groupNews);
        withheld.put("kept to a user type", typeNews);
        withheld.put("kept to a tag", tagNews);
        withheld.put("of another station", elsewhereNews);
        return withheld;
    }

    /** The appointments neither a page nor an article may name, by what makes them so. */
    protected static Map<String, StationEvent> eventsNobodyMayName() {
        var withheld = new LinkedHashMap<String, StationEvent>();
        withheld.put("kept to a group", groupEvent);
        withheld.put("kept to a user type", typeEvent);
        withheld.put("kept to a tag", tagEvent);
        withheld.put("of another station", elsewhereEvent);
        return withheld;
    }

    /** The public id an event block names the appointment by. */
    protected static UUID uidOf(StationEvent event) {
        return eventRepo
                .findPublicUidsByIds(event.stationId(), List.of(event.id()))
                .get(event.id());
    }

    /**
     * @param publishedAt when the entry was published, or null for a draft
     */
    private static News news(Station owner, String title, boolean onTheBlog, String publishedAt) {
        var news = newsRepo.create(owner.id(), title, "Mehr im Text", "<p>Mehr im Text</p>", null);
        newsRepo.updatePublicBlog(news.id(), onTheBlog);
        Query.query("""
                        UPDATE news
                        SET published_at = :published_at
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind(
                                "published_at",
                                publishedAt == null ? null : Instant.parse(publishedAt),
                                INSTANT_TIMESTAMP)
                        .bind("id", news.id()))
                .update();
        return newsRepo.findById(news.id()).orElseThrow();
    }

    private static StationEvent event(Station owner, String name, boolean onThePublicCalendar) {
        var start = Instant.now().plus(20, ChronoUnit.DAYS);
        var event = eventRepo.create(
                owner.id(),
                name,
                name + " im Text",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        Query.query("""
                        UPDATE station_event
                        SET public = :public
                        WHERE id = :id;""")
                .single(Call.of().bind("public", onThePublicCalendar).bind("id", event.id()))
                .update();
        return event;
    }

    private static void restrict(RestrictionType type, int id, RestrictionSelection selection) {
        restrictionRepo.setRestrictions(type, id, selection);
    }

    private static RestrictionSelection onlyGroup(int groupId) {
        return new RestrictionSelection(List.of(), List.of(groupId), List.of(), List.of(), null);
    }

    private static RestrictionSelection onlyType() {
        return new RestrictionSelection(List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), null);
    }

    private static RestrictionSelection onlyTag(int tagId) {
        return new RestrictionSelection(List.of(), List.of(), List.of(tagId), List.of(), null);
    }
}
