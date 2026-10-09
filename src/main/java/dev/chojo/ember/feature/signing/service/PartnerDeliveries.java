/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.signing.entity.AgreementNoticeKind;
import dev.chojo.ember.feature.signing.entity.PartnerSigning;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementField;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementNotice;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.route.RemoteSigningRoutes;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Sends the station holding a partner's appointment where a document its appointment asks a member here to
 * sign stands: that it was taken on, and then every sealed state of it, the newest one each time.
 *
 * <p>Every notice is a federation request, signed with this station's federation key. A sealed copy carries
 * the signers' official names on its record page and its evidence attached; the notice says nothing more of
 * the member than the id the registration names.
 *
 * <p>A new sealed state is sent at once, outside the transaction that filed it ({@link SealedStateFollowUp}).
 * Whatever did not reach the partner, because it was unreachable or refused, is tried again by a sweep, later
 * after every failure in a row (five minutes, doubling up to six hours), and given up after
 * {@link #MAX_ATTEMPTS} failures, about a week, until the next sealed state starts it again. Nothing travels
 * once the partnership is gone or not active.
 */
@Singleton
public class PartnerDeliveries implements SealedStateFollowUp, TaskSource {
    /** The failures in a row after which sending is given up. */
    static final int MAX_ATTEMPTS = 32;

    private static final Duration FIRST_RETRY = Duration.ofMinutes(5);
    private static final Duration LONGEST_RETRY = Duration.ofHours(6);
    private static final Duration START_DELAY = Duration.ofMinutes(3);
    private static final Duration INTERVAL = Duration.ofMinutes(5);
    private static final int SWEEP_LIMIT = 50;
    private static final Logger log = LoggerFactory.getLogger(PartnerDeliveries.class);

    private final PartnerSigningRepository links;
    private final SignatureRequestRepository requests;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final StationMemberRepository members;
    private final FederationRepository partners;
    private final FederationTransport transport;
    private final Executor executor;
    private final Clock clock;

    @Inject
    public PartnerDeliveries(
            PartnerSigningRepository links,
            SignatureRequestRepository requests,
            DocumentRepository documents,
            DocumentService documentService,
            StationMemberRepository members,
            FederationRepository partners,
            FederationTransport transport,
            TaskScheduler scheduler) {
        this(
                links,
                requests,
                documents,
                documentService,
                members,
                partners,
                transport,
                scheduler.executor(),
                Clock.systemUTC());
    }

    /** Builds the service with its own executor and clock, so a test sends on the calling thread. */
    PartnerDeliveries(
            PartnerSigningRepository links,
            SignatureRequestRepository requests,
            DocumentRepository documents,
            DocumentService documentService,
            StationMemberRepository members,
            FederationRepository partners,
            FederationTransport transport,
            Executor executor,
            Clock clock) {
        this.links = links;
        this.requests = requests;
        this.documents = documents;
        this.documentService = documentService;
        this.members = members;
        this.partners = partners;
        this.transport = transport;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public void sealed(int requestId) {
        var link = links.forRequest(requestId);
        if (link.isEmpty()) return;
        links.sendAgain(requestId);
        executor.execute(() -> deliver(link.get()));
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "partner-agreement-delivery", Schedule.fixedDelay(START_DELAY, INTERVAL), this::sweep));
    }

    /**
     * Tells the partner that a document was taken on, in the background.
     *
     * @param link the request and what it answers
     */
    void announce(PartnerSigning link) {
        executor.execute(() -> deliver(link));
    }

    /**
     * Sends what is due and has not reached its partner yet.
     *
     * @return how many notices reached their partner
     */
    int sweep() {
        int reached = 0;
        for (var link : links.due(clock.instant(), MAX_ATTEMPTS, SWEEP_LIMIT)) {
            if (deliver(link)) reached++;
        }
        return reached;
    }

    /**
     * Sends the partner where the request stands: the newest sealed copy where one is filed, else that it was
     * taken on.
     *
     * @return whether the partner took it
     */
    boolean deliver(PartnerSigning link) {
        var partner = activePartner(link);
        var request = requests.findById(link.requestId());
        var member = request.map(SignatureRequest::memberId).flatMap(members::findById);
        if (partner.isEmpty() || request.isEmpty() || member.isEmpty()) return false;
        byte[] sealed = sealedCopy(request.get()).orElse(null);
        var notice = noticeOf(link, request.get(), member.get(), sealed);
        try {
            transport.deliver(partner.get(), RemoteSigningRoutes.AGREEMENT_NOTICE.at(link.remoteEventId()), notice);
        } catch (RuntimeException e) {
            var retry = clock.instant().plus(delayAfter(link.deliveryAttempts()));
            links.failed(link.id(), retry);
            log.warn(
                    "Partner {} did not take where signing request {} stands (try {}), next at {}: {}",
                    link.partnerId(),
                    request.get().uid(),
                    link.deliveryAttempts() + 1,
                    retry,
                    String.valueOf(e.getMessage()));
            return false;
        }
        links.delivered(link.id(), sealed == null ? null : Sha256.hex(sealed));
        log.info(
                "Partner {} took {} of signing request {}",
                link.partnerId(),
                notice.kind(),
                request.get().uid());
        return true;
    }

    private Optional<FederationPartner> activePartner(PartnerSigning link) {
        Integer partnerId = link.partnerId();
        if (partnerId == null) return Optional.empty();
        return partners.findPartnerById(partnerId).filter(partner -> partner.status() == FederationStatus.ACTIVE);
    }

    private Optional<byte[]> sealedCopy(SignatureRequest request) {
        Integer documentId = request.documentId();
        if (documentId == null) return Optional.empty();
        return documents.findById(documentId).filter(Document::sealed).flatMap(documentService::read);
    }

    private RemoteAgreementNotice noticeOf(
            PartnerSigning link, SignatureRequest request, StationMember member, byte[] sealed) {
        var fields = requests.fieldsOf(request.id()).stream()
                .map(field -> new RemoteAgreementField(field.fieldName(), field.state(), field.settledAt()))
                .toList();
        return new RemoteAgreementNotice(
                member.uid(),
                link.eventDate(),
                link.remoteTemplateId(),
                link.templateVersion(),
                request.contentSha256(),
                sealed == null ? AgreementNoticeKind.ASKED : AgreementNoticeKind.SIGNED,
                sealed == null ? null : Base64.getEncoder().encodeToString(sealed),
                fields,
                request.state() == RequestState.COMPLETE);
    }

    /** Five minutes after the first failure, doubling with every further one up to six hours. */
    static Duration delayAfter(int failuresBefore) {
        int doublings = Math.min(failuresBefore, 10);
        var delay = FIRST_RETRY.multipliedBy(1L << doublings);
        return delay.compareTo(LONGEST_RETRY) > 0 ? LONGEST_RETRY : delay;
    }
}
