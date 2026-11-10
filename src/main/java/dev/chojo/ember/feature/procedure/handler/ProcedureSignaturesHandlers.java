/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.SigningAgreementWithdrawn;
import dev.chojo.ember.event.events.SigningRequestClosed;
import dev.chojo.ember.event.events.SigningRequestReplaced;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * What the signatures on a document step's document mean for the step: agreed ticks it, a withdrawn
 * agreement opens it again, and a corrected document takes the place of the one it corrects.
 *
 * <p>The procedures are reached through a provider, since the procedure service publishes on the very bus
 * these handlers are registered with.
 */
public final class ProcedureSignaturesHandlers {
    private ProcedureSignaturesHandlers() {}

    /** Ticks the step whose document stands as agreed. */
    @Singleton
    public static class Closed implements DomainEventHandler<SigningRequestClosed> {
        private final Provider<ProcedureService> procedures;

        @Inject
        public Closed(Provider<ProcedureService> procedures) {
            this.procedures = procedures;
        }

        @Override
        public Class<SigningRequestClosed> eventType() {
            return SigningRequestClosed.class;
        }

        @Override
        public void handle(SigningRequestClosed event) {
            var generationId = event.generationId();
            if (generationId == null) return;
            procedures.get().documentClosed(generationId, event.agreed(), event.settledBy());
        }
    }

    /** Opens the step whose agreement was withdrawn. */
    @Singleton
    public static class Withdrawn implements DomainEventHandler<SigningAgreementWithdrawn> {
        private final Provider<ProcedureService> procedures;

        @Inject
        public Withdrawn(Provider<ProcedureService> procedures) {
            this.procedures = procedures;
        }

        @Override
        public Class<SigningAgreementWithdrawn> eventType() {
            return SigningAgreementWithdrawn.class;
        }

        @Override
        public void handle(SigningAgreementWithdrawn event) {
            var generationId = event.generationId();
            if (generationId == null) return;
            procedures.get().agreementWithdrawn(generationId, event.withdrawnBy());
        }
    }

    /** Moves the step on to the corrected document. */
    @Singleton
    public static class Replaced implements DomainEventHandler<SigningRequestReplaced> {
        private final Provider<ProcedureService> procedures;

        @Inject
        public Replaced(Provider<ProcedureService> procedures) {
            this.procedures = procedures;
        }

        @Override
        public Class<SigningRequestReplaced> eventType() {
            return SigningRequestReplaced.class;
        }

        @Override
        public void handle(SigningRequestReplaced event) {
            var replaced = event.replacedGenerationId();
            var generationId = event.generationId();
            if (replaced == null || generationId == null) return;
            procedures.get().documentReplaced(replaced, generationId);
        }
    }
}
