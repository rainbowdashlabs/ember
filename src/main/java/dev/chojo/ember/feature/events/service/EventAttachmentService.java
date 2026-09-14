/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What an event hands over, and who is handed it.
 *
 * <p>A file is either for everybody who may see the event or kept back from the room. Which of the
 * two a reader gets is decided here and nowhere else, so the list a screen draws and the answer a
 * download gives cannot disagree: a file left out of the list is refused when its address is asked
 * for directly.
 */
@Singleton
public class EventAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(EventAttachmentService.class);

    private final EventAttachmentRepository repository;
    private final MediaLibraryService media;

    @Inject
    public EventAttachmentService(EventAttachmentRepository repository, MediaLibraryService media) {
        this.repository = repository;
        this.media = media;
    }

    /**
     * Whether a reader holding these permissions may be handed what an event keeps back.
     *
     * @param permissions the reader's permissions, already expanded
     */
    public static boolean readsInternal(Collection<StationPermission> permissions) {
        return permissions.contains(StationPermission.EVENT_INTERNAL);
    }

    /**
     * The files of an event as this reader may have them.
     *
     * @param permissions the reader's permissions, already expanded
     */
    public List<EventAttachment> listFor(int eventId, Collection<StationPermission> permissions) {
        return visible(repository.findByEvent(eventId), readsInternal(permissions));
    }

    /** Every file of an event, for whoever is editing it. */
    public List<EventAttachment> listAll(int eventId) {
        return repository.findByEvent(eventId);
    }

    /** The files that leave the station: what an event hands the room, and never what it keeps back. */
    public List<EventAttachment> listOpen(int eventId) {
        return visible(repository.findByEvent(eventId), false);
    }

    /**
     * The open files of several events at once, so a listing does not ask once per row.
     */
    public Map<Integer, List<EventAttachment>> listOpenFor(List<Integer> eventIds) {
        var byEvent = repository.findByEventIds(eventIds);
        byEvent.replaceAll((eventId, attachments) -> visible(attachments, false));
        return byEvent;
    }

    public Optional<EventAttachment> find(int attachmentId) {
        return repository.findById(attachmentId);
    }

    /**
     * The file behind an attachment, where this reader may be handed it.
     *
     * @param permissions the reader's permissions, already expanded
     * @return the attachment, or empty where there is none or it is kept back from this reader
     */
    public Optional<EventAttachment> findReadable(int attachmentId, Collection<StationPermission> permissions) {
        return repository
                .findById(attachmentId)
                .filter(attachment -> !attachment.internal() || readsInternal(permissions));
    }

    /**
     * Attaches a file from the station's library to the event. The file has to belong to the same
     * station, because an attachment is a reference into that station's library and nothing else.
     */
    public EventAttachment attach(int eventId, int stationId, int fileId, String label, boolean internal) {
        var file = media.findFile(fileId).orElseThrow(() -> new BadRequestResponse("Unknown file"));
        if (file.stationId() != stationId) throw new BadRequestResponse("File belongs to another station");
        var attachment = repository.attach(eventId, fileId, blankToNull(label), internal);
        log.info(
                "File {} attached to event {} in station {} ({})",
                fileId,
                eventId,
                stationId,
                internal ? "internal" : "open");
        return attachment;
    }

    public boolean update(int attachmentId, String label, boolean internal) {
        return repository.update(attachmentId, blankToNull(label), internal);
    }

    public void reorder(int eventId, List<Integer> attachmentIds) {
        repository.reorder(eventId, attachmentIds);
    }

    public boolean detach(int attachmentId) {
        return repository.detach(attachmentId);
    }

    private static List<EventAttachment> visible(List<EventAttachment> attachments, boolean withInternal) {
        return attachments.stream()
                .filter(attachment -> withInternal || !attachment.internal())
                .toList();
    }

    private static String blankToNull(String label) {
        return label == null || label.isBlank() ? null : label;
    }
}
