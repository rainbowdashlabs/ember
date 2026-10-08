/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.StationSigner;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.signing.entity.PinOutcome;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityStatement;
import dev.chojo.ember.feature.signing.entity.StatedAuthority;
import dev.chojo.ember.feature.signing.entity.StatementRefusal;
import dev.chojo.ember.feature.signing.entity.StoredAuthorityCertificate;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository.NewPin;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.signing.route.RemoteSigningRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.RandomTokens;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.chojo.ember.feature.signing.entity.StatementRefusal.NOT_AN_AUTHORITY;

/**
 * The signing authorities of federation partners: what a station states about its own installation's
 * authorities when a partner asks, and what it pins of a partner's when it asks.
 *
 * <p><b>Why a pin can be trusted.</b> A station asks its partner over the federation channel with a fresh
 * random challenge. The partner answers with every authority of its installation, signed with its
 * federation key over the challenge, both stations and every certificate and revocation list by its hash
 * ({@link AuthorityStatements}). The asking station checks that signature against the key it holds from
 * the pairing. So the authorities are bound to the partnership's federation key: whoever sits between the
 * two installations can neither swap an authority in nor play an old statement back. Trust therefore rests
 * on the pairing, which is the moment the partner's key was taken; an authority is pinned the first time a
 * statement is taken from the partner ({@code FIRST_FETCH}), whenever that is.
 *
 * <p><b>Changes.</b> A partner's installation renews its authority, or gives one up and issues a new one
 * after its key file was lost. A changed authority is taken only from such a signed statement, never from
 * the partner's public addresses: an authority a statement names for the first time is pinned beside the
 * earlier ones ({@code ANNOUNCED}). A statement never removes a pin. Documents sealed under an authority the
 * partner later gave up keep being checked against it, with its last revocation list taken; a timestamp
 * decides, as for any seal, whether a seal predates a revocation.
 *
 * <p><b>Revocation lists.</b> Every statement carries each authority's current list, checked against that
 * authority before it is stored, and an older list never replaces a newer one. Asking happens within
 * {@link #BUDGET}, so a partner that does not answer holds nobody up: the pins and lists held stay as they
 * are. Partnerships with pins are asked once a day, so the lists stay current for every check.
 *
 * <p>A partnership whose partner key is unknown, as for a pair a cluster made, takes no statement at all.
 */
@Singleton
public class PartnerAuthorities implements FederationServer, TaskSource {
    /** How long asking a partner may take before the pins held are used as they are. */
    public static final Duration BUDGET = Duration.ofSeconds(10);

    /** How long pins are used without asking the partner again. */
    static final Duration RECHECK = Duration.ofDays(1);

    private static final Duration START_DELAY = Duration.ofMinutes(30);
    private static final Logger log = LoggerFactory.getLogger(PartnerAuthorities.class);

    private final FederationTransport transport;
    private final FederationRepository partners;
    private final StationRepository stations;
    private final StationSigner signer;
    private final FederationSigningService signing;
    private final SigningKeyRepository keys;
    private final StationKeyRevocations revocations;
    private final PartnerAuthorityRepository pins;
    private final Executor executor;
    private final Clock clock;
    private final Duration budget;

    /**
     * @param transport   reaches the partners
     * @param partners    the partnerships
     * @param stations    resolves the stations' uids
     * @param signer      signs this installation's statements as a station
     * @param signing     checks the partners' signatures
     * @param keys        this installation's authorities
     * @param revocations their current revocation lists
     * @param pins        the partners' pinned authorities
     * @param scheduler   runs the questions to partners, so they can be given up after the budget
     */
    @Inject
    public PartnerAuthorities(
            FederationTransport transport,
            FederationRepository partners,
            StationRepository stations,
            StationSigner signer,
            FederationSigningService signing,
            SigningKeyRepository keys,
            StationKeyRevocations revocations,
            PartnerAuthorityRepository pins,
            TaskScheduler scheduler) {
        this(
                transport,
                partners,
                stations,
                signer,
                signing,
                keys,
                revocations,
                pins,
                scheduler.executor(),
                Clock.systemUTC(),
                BUDGET);
    }

    /**
     * Builds the service with its own clock and budget, so a test can stand in a partner that does not
     * answer without waiting out the production budget.
     */
    PartnerAuthorities(
            FederationTransport transport,
            FederationRepository partners,
            StationRepository stations,
            StationSigner signer,
            FederationSigningService signing,
            SigningKeyRepository keys,
            StationKeyRevocations revocations,
            PartnerAuthorityRepository pins,
            Executor executor,
            Clock clock,
            Duration budget) {
        this.transport = transport;
        this.partners = partners;
        this.stations = stations;
        this.signer = signer;
        this.signing = signing;
        this.keys = keys;
        this.revocations = revocations;
        this.pins = pins;
        this.executor = executor;
        this.clock = clock;
        this.budget = budget;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteSigningRoutes.AUTHORITIES,
                (partner, params, body) ->
                        statementFor(partner, params.query("challenge").orElse(null)));
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "partner-signing-authorities", Schedule.fixedDelay(START_DELAY, RECHECK), this::refreshPinned));
    }

    /**
     * States this installation's authorities to a partner that asked, signed as the station it asked.
     *
     * @param asking    the partnership as the asked station sees it
     * @param challenge the asking station's challenge
     * @return the signed statement; its list is empty while no station here sealed anything
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the challenge is malformed or the station has
     *     no federation key to sign with
     */
    SigningAuthorityStatement statementFor(ServingPartner asking, @Nullable String challenge) {
        if (challenge == null || !AuthorityStatements.wellFormedChallenge(challenge)) {
            throw DocumentRefusal.AUTHORITY_CHALLENGE_MALFORMED.raise();
        }
        int stationId = asking.servingStationId();
        if (!signer.canSign(stationId)) throw DocumentRefusal.AUTHORITIES_CANNOT_BE_VOUCHED_FOR.raise();
        var station = stations.requireUid(stationId);
        var issuedAt = clock.instant().toString();
        var stated = keys.authorityCertificates().stream().map(this::stated).toList();
        var text = AuthorityStatements.text(station, asking.askingStationUid(), challenge, issuedAt, stated);
        return new SigningAuthorityStatement(
                station, asking.askingStationUid(), challenge, issuedAt, stated, signer.signStatement(stationId, text));
    }

    /**
     * Asks a partner for its authorities and pins what its signed statement names, within {@link #BUDGET}.
     *
     * @param partner the asking station's partnership
     * @return what came of it; the pins held are unchanged unless it was taken
     */
    public PinOutcome refresh(FederationPartner partner) {
        var challenge = RandomTokens.hex(AuthorityStatements.CHALLENGE_BYTES);
        var request = RemoteSigningRoutes.AUTHORITIES.at().query("challenge", challenge);
        var answer = CompletableFuture.supplyAsync(
                () -> transport.get(partner, request, SigningAuthorityStatement.class), executor);
        try {
            return take(partner, answer.get(budget.toMillis(), TimeUnit.MILLISECONDS), challenge);
        } catch (TimeoutException e) {
            answer.cancel(true);
            log.warn("Partner {} did not state its signing authorities within {}", partner.id(), budget);
            return PinOutcome.unanswered();
        } catch (ExecutionException e) {
            log.warn(
                    "Partner {} did not state its signing authorities: {}", partner.id(), String.valueOf(e.getCause()));
            return PinOutcome.unanswered();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PinOutcome.unanswered();
        }
    }

    /**
     * Whether the pins of a partnership are due to be asked for again before a check: there are none,
     * the partner has not been asked for a day, or a revocation list is missing or overdue.
     *
     * @param partnerId the partnership
     * @return true when the partner should be asked first
     */
    boolean due(int partnerId) {
        var now = clock.instant();
        var pinned = pins.pinnedFor(partnerId);
        return pinned.isEmpty()
                || pinned.stream()
                        .anyMatch(pin -> pin.lastListedAt().plus(RECHECK).isBefore(now) || pin.revocationListDue(now));
    }

    /**
     * Checks a statement a partner sent against the challenge it was asked with and pins what it names.
     *
     * @param partner   the asking station's partnership
     * @param statement the statement as received
     * @param challenge the challenge it was asked with
     * @return whether it was taken and what it pinned for the first time
     */
    PinOutcome take(FederationPartner partner, SigningAuthorityStatement statement, String challenge) {
        var refusal = refusalOf(partner, statement, challenge);
        if (refusal.isPresent()) {
            log.warn("Refused the signing authorities partner {} stated: {}", partner.id(), refusal.get());
            return PinOutcome.refused(refusal.get());
        }
        var authorities = authoritiesOf(statement);
        if (authorities.isEmpty()) {
            log.warn("Refused the signing authorities partner {} stated: {}", partner.id(), NOT_AN_AUTHORITY);
            return PinOutcome.refused(NOT_AN_AUTHORITY);
        }
        return pin(partner, statement, authorities.get());
    }

    private PinOutcome pin(
            FederationPartner partner, SigningAuthorityStatement statement, List<X509Certificate> authorities) {
        var at = clock.instant();
        var text = AuthorityStatements.text(statement);
        var newlyPinned = Transactions.call(() -> {
            var pinned = new ArrayList<String>();
            for (int i = 0; i < authorities.size(); i++) {
                var stated = statement.authorities().get(i);
                var sha256 = Sha256.hex(stated.certificate());
                var pin = new NewPin(sha256, stated.certificate(), stated.active(), at, text, statement.signature());
                if (pins.pin(partner.id(), pin)) {
                    pinned.add(sha256);
                } else {
                    pins.listed(partner.id(), sha256, stated.active(), at);
                }
                takeRevocationList(partner.id(), sha256, stated, authorities.get(i));
            }
            return pinned;
        });
        if (!newlyPinned.isEmpty()) {
            log.info("Pinned {} signing authorities of partner {}", newlyPinned.size(), partner.id());
        }
        return PinOutcome.taken(newlyPinned);
    }

    private Optional<StatementRefusal> refusalOf(
            FederationPartner partner, SigningAuthorityStatement statement, String challenge) {
        var partnerKey = partner.partnerPublicKey();
        if (partnerKey == null) return Optional.of(StatementRefusal.NO_PARTNER_KEY);
        if (!partner.partnerStationId().equals(statement.stationUid())) {
            return Optional.of(StatementRefusal.WRONG_STATION);
        }
        if (!stations.requireUid(partner.stationId()).equals(statement.recipientUid())) {
            return Optional.of(StatementRefusal.WRONG_RECIPIENT);
        }
        if (!challenge.equals(statement.challenge())) return Optional.of(StatementRefusal.WRONG_CHALLENGE);
        if (!isMoment(statement.issuedAt())) return Optional.of(StatementRefusal.NO_MOMENT);
        if (!signing.statementHolds(AuthorityStatements.text(statement), statement.signature(), partnerKey)) {
            return Optional.of(StatementRefusal.SIGNATURE);
        }
        return Optional.empty();
    }

    /**
     * Reads every authority a statement names, each with the revocation list stated for it.
     *
     * @return the authorities in the statement's order, or empty when one is no self-signed authority or a
     *     list stated for it is not that authority's
     */
    private static Optional<List<X509Certificate>> authoritiesOf(SigningAuthorityStatement statement) {
        var authorities = new ArrayList<X509Certificate>();
        for (var stated : statement.authorities()) {
            var authority = AuthorityStatements.authorityOf(stated.certificate());
            if (authority.isEmpty()) return Optional.empty();
            var list = stated.revocationList();
            if (list != null
                    && AuthorityStatements.revocationListOf(list, authority.get())
                            .isEmpty()) {
                return Optional.empty();
            }
            authorities.add(authority.get());
        }
        return Optional.of(authorities);
    }

    private void takeRevocationList(int partnerId, String sha256, StatedAuthority stated, X509Certificate authority) {
        var der = stated.revocationList();
        if (der == null) return;
        AuthorityStatements.revocationListOf(der, authority)
                .ifPresent(list -> pins.takeRevocationList(
                        partnerId,
                        sha256,
                        der,
                        list.getThisUpdate().toInstant(),
                        list.getNextUpdate().toInstant()));
    }

    private StatedAuthority stated(StoredAuthorityCertificate authority) {
        return new StatedAuthority(authority.certificate(), authority.active(), currentList(authority.serialNumber()));
    }

    private byte @Nullable [] currentList(String serialNumber) {
        try {
            return revocations.revocationList(serialNumber).orElse(null);
        } catch (SigningKeyWrapException e) {
            log.warn("Stated authority {} to a partner without its revocation list: {}", serialNumber, e.getMessage());
            return null;
        }
    }

    private void refreshPinned() {
        for (int partnerId : pins.partnersWithPins()) {
            partners.findPartnerById(partnerId)
                    .filter(partner -> partner.status() == FederationStatus.ACTIVE)
                    .ifPresent(this::refresh);
        }
    }

    private static boolean isMoment(String text) {
        try {
            Instant.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
