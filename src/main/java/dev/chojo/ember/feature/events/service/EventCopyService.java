/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCopyRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * Copying an appointment into a new one.
 *
 * <p>The editor opens on a copy with everything it shows filled in from the original, and the save
 * writes that as it writes any new appointment: settings, audiences, fields, questions, reminders and
 * the partner share. What the editor of a new appointment cannot hold is handed over here once the
 * copy exists: the attached files, which are linked rather than duplicated, the documents to bring and
 * the equipment needed on every date.
 *
 * <p>Nothing that belongs to the dates of the original is taken: sign-ups and their answers, sheets,
 * comments, notes, called-off and detached dates, per-date answers and per-date equipment. The copy
 * is no part of an event group either, whatever the original is.
 */
@Singleton
public class EventCopyService {
    private static final Logger log = LoggerFactory.getLogger(EventCopyService.class);

    private final EventRepository eventRepository;
    private final EventCopyRepository copyRepository;

    @Inject
    public EventCopyService(EventRepository eventRepository, EventCopyRepository copyRepository) {
        this.eventRepository = eventRepository;
        this.copyRepository = copyRepository;
    }

    /**
     * The appointment a new one is copied from, asked before the copy is written so a refused original
     * leaves no half-made appointment behind.
     *
     * @param stationId the station making the copy
     * @param eventId   the original it names
     * @param readable  whether the one copying may see an appointment of their station
     * @return the original
     * @throws io.javalin.http.HttpResponseException where the original is gone, another station's or
     *                                               hidden from the caller, in the same words for all
     */
    public StationEvent requireSource(int stationId, int eventId, Predicate<StationEvent> readable) {
        return eventRepository
                .findById(eventId)
                .filter(event -> event.stationId() == stationId)
                .filter(readable)
                .orElseThrow(EventRefusal.EVENT_TO_COPY_NOT_HERE::raise);
    }

    /**
     * Hands the original's files, documents to bring and standing equipment lines to the copy, in one
     * transaction.
     *
     * @param source        the original, as {@link #requireSource} found it
     * @param targetEventId the copy, already written
     */
    public void copyInto(StationEvent source, int targetEventId) {
        Transactions.run(() -> {
            int files = copyRepository.copyAttachments(source.id(), targetEventId);
            int documents = copyRepository.copyDocumentRequirements(source.id(), targetEventId);
            int needs = copyRepository.copyEquipmentNeeds(source.id(), targetEventId);
            log.info(
                    "Event {} copied from event {}: {} file(s), {} document(s), {} equipment line(s)",
                    targetEventId,
                    source.id(),
                    files,
                    documents,
                    needs);
        });
    }
}
