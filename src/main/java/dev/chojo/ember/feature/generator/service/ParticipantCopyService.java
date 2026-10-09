/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

/**
 * A participant's copy of a document an appointment asks for, as whoever manages the registrations reads it
 * without the right to read member documents: the latest copy filed for the date, served as its current
 * sealed version once it was signed online and as it was filed before that. Only a document the appointment
 * asks for, of a participant of the date, filed at the reader's station, is served.
 */
@Singleton
public class ParticipantCopyService {
    private final AppointmentDocumentService appointments;
    private final EventRequirementRepository requirements;
    private final DocumentCatalogService catalog;
    private final DocumentService documents;

    @Inject
    public ParticipantCopyService(
            AppointmentDocumentService appointments,
            EventRequirementRepository requirements,
            DocumentCatalogService catalog,
            DocumentService documents) {
        this.appointments = appointments;
        this.requirements = requirements;
        this.catalog = catalog;
        this.documents = documents;
    }

    /**
     * A filed copy and the bytes it serves.
     *
     * @param document the member document the copy was filed as
     * @param data     its current sealed version, or the file as filed where it was never sealed
     */
    public record Copy(Document document, byte[] data) {}

    /**
     * The latest copy of a document the appointment asks for, filed for a participant on the date.
     *
     * @param session    the manager of the registrations
     * @param event      the appointment, already checked to be one the reader may see
     * @param date       the date of the appointment
     * @param templateId the document asked for
     * @param memberId   the participant
     * @return the copy, refusing a participant of another date, a document the appointment does not ask
     *         for and a copy that was never filed
     */
    public Copy copyOf(StationSession session, StationEvent event, LocalDate date, int templateId, int memberId) {
        appointments.requireMayHandIn(session, event, date, memberId, true);
        appointments.requireAsked(session.stationId(), event.id(), templateId);
        var filed = requirements.latest(event.id(), date, List.of(memberId)).stream()
                .filter(copy -> copy.templateId() == templateId)
                .findFirst()
                .orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        var document = catalog.find(filed.documentId())
                .filter(found -> found.stationId() == session.stationId())
                .orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        var data = documents
                .open(document, DocumentDoor.STATION)
                .orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        return new Copy(document, data);
    }
}
