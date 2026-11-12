/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.DateSpan;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteChangedMember;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteEventChange;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteEventHandover;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells partner stations that a shared appointment their members hold a place on changed, and takes
 * in the same word from the partners sharing appointments with this station.
 *
 * <p>The station that runs the appointment does not know a partner's members by name and cannot
 * reach them. It tells their station which of its members are concerned and from which date, and
 * that station tells them and whoever looks after them, the way it tells them anything else. What is
 * told follows the change notice: the change where the editor chose to tell it, otherwise only a
 * moved start.
 *
 * <p>Only a partner's members holding a place are told. On an appointment without registration, a
 * partner's member who can see it but holds no place hears nothing, since the station running it
 * does not know them.
 */
@Singleton
public class FederatedEventChangeService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(FederatedEventChangeService.class);

    /** How long a note may be when it arrives from a partner, the same as when it is written here. */
    private static final int NOTE_LIMIT = 500;

    private final EventFederationRepository eventFederationRepository;
    private final FederationRepository federationRepository;
    private final FederationTransport transport;
    private final StationMemberRepository stationMemberRepository;
    private final MemberNameResolver nameResolver;
    private final Notifier notifier;
    private final PartnerSigningRepository partnerSigning;

    @Inject
    public FederatedEventChangeService(
            EventFederationRepository eventFederationRepository,
            FederationRepository federationRepository,
            FederationTransport transport,
            StationMemberRepository stationMemberRepository,
            MemberNameResolver nameResolver,
            Notifier notifier,
            PartnerSigningRepository partnerSigning) {
        this.partnerSigning = partnerSigning;
        this.eventFederationRepository = eventFederationRepository;
        this.federationRepository = federationRepository;
        this.transport = transport;
        this.stationMemberRepository = stationMemberRepository;
        this.nameResolver = nameResolver;
        this.notifier = notifier;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteEventChange, Void>serve(RemoteEventRoutes.EVENT_CHANGED_WEBHOOK, (partner, params, body) -> {
            received(partner, body);
            return null;
        });
        endpoints.<RemoteEventHandover, Void>serve(
                RemoteEventRoutes.EVENT_CONTINUED_WEBHOOK, (partner, params, body) -> {
                    partnerSigning.followHandover(
                            partner.servingStationId(),
                            partner.askingStationUid(),
                            body.fromEventId(),
                            body.toEventId(),
                            body.from(),
                            body.until());
                    return null;
                });
    }

    /**
     * Tells the partner stations whose members hold places on dates an appointment handed to another
     * one, so their records of those places name the other appointment from now. Sent whatever the
     * editor chose to tell, because it is bookkeeping rather than news.
     *
     * @param fromEventId the appointment the dates were taken from
     * @param toEventId   the appointment that holds them now, which already holds the partners' places
     * @param dates       the dates handed over
     */
    public void tellHandover(int fromEventId, int toEventId, DateSpan dates) {
        var partners = eventFederationRepository.findRegistrations(toEventId, null).stream()
                .map(EventFederationRegistration::partnerId)
                .distinct()
                .toList();
        var handover = new RemoteEventHandover(fromEventId, toEventId, dates.from(), dates.until());
        for (int partnerId : partners) {
            federationRepository
                    .findPartnerById(partnerId)
                    .ifPresent(partner ->
                            transport.notify(partner, RemoteEventRoutes.EVENT_CONTINUED_WEBHOOK.at(), handover));
        }
    }

    /**
     * Tells every partner station whose members hold a place on the touched dates that the
     * appointment changed.
     *
     * @param event        the appointment as it now stands
     * @param first        the first date the change touches
     * @param changedParts the changed parts, joined the way the change notice stores them
     * @param note         the editor's note, or null
     * @param startTime    the new start where it moved, or null
     * @param told         whether the editor chose to tell about the change
     */
    public void tellPartners(
            StationEvent event,
            LocalDate first,
            String changedParts,
            @Nullable String note,
            @Nullable String startTime,
            boolean told) {
        if (!told && startTime == null) return;
        for (var entry : holdersByPartner(event, first).entrySet()) {
            var partner = federationRepository.findPartnerById(entry.getKey()).orElse(null);
            if (partner == null) continue;
            transport.notify(
                    partner,
                    RemoteEventRoutes.EVENT_CHANGED_WEBHOOK.at(),
                    new RemoteEventChange(
                            event.id(), event.name(), changedParts, note, startTime, told, entry.getValue()));
            log.info("Told partner {} about the change of event {}", entry.getKey(), event.id());
        }
    }

    /**
     * The partners' members holding a place from the first touched date on, each once with the first
     * date they hold, per partner. A one-off holds only its own date.
     */
    private Map<Integer, List<RemoteChangedMember>> holdersByPartner(StationEvent event, LocalDate first) {
        var firstHeld = new LinkedHashMap<Integer, Map<UUID, LocalDate>>();
        eventFederationRepository.findRegistrations(event.id(), null).stream()
                .filter(FederatedEventChangeService::holdsPlace)
                .filter(registration -> event.isRecurring()
                        ? !registration.eventDate().isBefore(first)
                        : registration.eventDate().equals(first))
                .sorted(Comparator.comparing(EventFederationRegistration::eventDate))
                .forEach(registration -> firstHeld
                        .computeIfAbsent(registration.partnerId(), partner -> new LinkedHashMap<>())
                        .putIfAbsent(registration.remoteMemberId(), registration.eventDate()));
        var byPartner = new LinkedHashMap<Integer, List<RemoteChangedMember>>();
        firstHeld.forEach((partner, members) -> {
            var list = new ArrayList<RemoteChangedMember>();
            members.forEach((uid, date) -> list.add(new RemoteChangedMember(uid, date)));
            byPartner.put(partner, list);
        });
        return byPartner;
    }

    private static boolean holdsPlace(EventFederationRegistration registration) {
        return registration.status() == RegistrationStatus.PENDING
                || registration.status() == RegistrationStatus.ACCEPTED;
    }

    /**
     * Tells this station's members a partner named that the partner's appointment changed. Only members
     * this station signed up there for that date are told: a partner is believed about this station's
     * members only where this station sent them.
     */
    private void received(ServingPartner partner, RemoteEventChange change) {
        int stationId = partner.row().stationId();
        String note = trimmed(change.note());
        var link = NotificationLinks.federatedEvent(partner.askingStationUid(), change.eventId());
        int told = 0;
        for (var member : change.members()) {
            var local = stationMemberRepository
                    .findByUid(stationId, member.memberUid())
                    .orElse(null);
            if (local == null) continue;
            if (!partnerSigning.signedUp(
                    local.id(), partner.askingStationUid(), change.eventId(), member.eventDate())) {
                continue;
            }
            String name = nameResolver.called(local.id());
            NotificationParams params;
            NotificationType type;
            if (change.told()) {
                var named = new NotificationParams.EventChangeNotice(
                        change.eventName(), member.eventDate(), change.changedParts(), null, null, name);
                params = NotificationParams.EventChangeNotice.of(
                        change.eventName(), member.eventDate(), named.parts(), note, change.startTime(), name);
                type = NotificationType.EVENT_CHANGED;
            } else {
                String startTime = change.startTime();
                if (startTime == null) continue;
                params = new NotificationParams.EventMoved(change.eventName(), member.eventDate(), startTime, name);
                type = NotificationType.EVENT_MOVED;
            }
            notifier.notify(
                    StationAudience.household(List.of(local.id())),
                    type,
                    NotificationData.of(params, link),
                    Delivery.EVERY_TIME);
            told++;
        }
        log.info(
                "Partner {} told {} member(s) about a change of its event {}",
                partner.askingStationUid(),
                told,
                change.eventId());
    }

    private static @Nullable String trimmed(@Nullable String note) {
        if (note == null || note.isBlank()) return null;
        String stripped = note.strip();
        return stripped.length() > NOTE_LIMIT ? stripped.substring(0, NOTE_LIMIT) : stripped;
    }
}
