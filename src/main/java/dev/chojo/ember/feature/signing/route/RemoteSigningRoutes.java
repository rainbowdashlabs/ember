/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.signing.entity.RemoteAgreement;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementNotice;
import dev.chojo.ember.feature.signing.entity.RemoteRequirementChange;
import dev.chojo.ember.feature.signing.entity.RemoteRequirementChangeAnswer;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityStatement;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server signing endpoints served to federation partners, through the serving functions of
 * {@code PartnerAuthorities}, {@code PartnerAgreements} and {@code PartnerSignatures}. Requests carry an RSA-signed envelope instead of a
 * user session, which covers the body: a signed copy sent back is signed with the sending station's
 * federation key.
 *
 * <p>They belong to the event sharing surface, since documents a partner seals reach an organiser only
 * through shared events.
 */
@Singleton
public class RemoteSigningRoutes implements Routes {

    /**
     * The signing authorities of the serving station's installation, stated against the asking station's
     * challenge (query parameter {@code challenge}) and signed with the serving station's federation key.
     */
    public static final FederationEndpoint AUTHORITIES = FederationEndpoint.get(
            FederationSurface.EVENT_SHARE, "/remote/signing/authorities", SigningAuthorityStatement.class);

    /**
     * The documents a shared appointment asks the asking partner's members to sign on a date (path parameter
     * {@code date}, ISO), each the one copy every partner's signer signs alike, with its bytes. Answered by
     * {@code PartnerAgreements}; an appointment that asks nothing of the kind answers an empty list.
     */
    public static final FederationEndpoint AGREEMENTS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE,
            "/remote/signing/events/{eventId}/dates/{date}/agreements",
            RemoteAgreement.class);

    /**
     * A member's home installation telling the station holding a shared appointment where a document it asks
     * the member to sign stands there: taken on, or signed with the sealed copy. Answered by
     * {@code PartnerAgreements}.
     */
    public static final FederationEndpoint AGREEMENT_NOTICE = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/signing/events/{eventId}/agreements",
            RemoteAgreementNotice.class,
            Void.class);

    /**
     * The station holding a shared appointment telling a partner that the documents it asks for changed: the
     * partner asks its members registered on dates still ahead for the documents added, lets the open requests
     * of those taken off go, and answers with what it let go. Answered by {@code PartnerSignatures}.
     */
    public static final FederationEndpoint REQUIREMENTS_CHANGED = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/signing/events/{eventId}/requirements",
            RemoteRequirementChange.class,
            RemoteRequirementChangeAnswer.class);

    public static final List<FederationEndpoint> CONTRACT =
            List.of(AUTHORITIES, AGREEMENTS, AGREEMENT_NOTICE, REQUIREMENTS_CHANGED);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteSigningRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(AUTHORITIES)
                .serve(AGREEMENTS)
                .serve(AGREEMENT_NOTICE)
                .serve(REQUIREMENTS_CHANGED));
    }
}
