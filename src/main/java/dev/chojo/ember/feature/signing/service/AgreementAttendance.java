/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a signed agreement means for the appointment that asked for it.
 *
 * <p>On an appointment that takes no registrations, signing its agreement counts as "I will come": a
 * refusal the member gave for the date is taken back, the same way the member takes one back on the
 * appointment's page. On one that takes registrations, signing anew takes the flag of an agreement withdrawn
 * before off the registration. A document no appointment asked for means nothing here.
 */
@Singleton
public class AgreementAttendance implements AgreementOutcomes {
    private static final Logger log = LoggerFactory.getLogger(AgreementAttendance.class);

    private final SignatureRequestRepository requests;
    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final EventRegistrationService registrationService;

    @Inject
    public AgreementAttendance(
            SignatureRequestRepository requests,
            EventRepository events,
            EventRegistrationRepository registrations,
            EventRegistrationService registrationService) {
        this.requests = requests;
        this.events = events;
        this.registrations = registrations;
        this.registrationService = registrationService;
    }

    @Override
    public void completed(SignatureRequest request) {
        var copy = requests.appointmentOf(request.id()).orElse(null);
        if (copy == null) return;
        var event = events.findById(copy.eventId()).orElse(null);
        if (event == null) return;
        if (event.requiresRegistration()) {
            registrations.clearAgreementWithdrawn(copy.eventId(), copy.eventDate(), copy.memberId());
            return;
        }
        registrations.findByEventAndDate(copy.eventId(), copy.eventDate()).stream()
                .filter(registration -> registration.memberId() == copy.memberId()
                        && registration.status() == RegistrationStatus.DECLINED)
                .findFirst()
                .ifPresent(refusal -> {
                    registrationService.withdraw(refusal.id());
                    log.info(
                            "Member {} signed the agreement of appointment {} on {}; their refusal is taken back",
                            copy.memberId(),
                            copy.eventId(),
                            copy.eventDate());
                });
    }
}
