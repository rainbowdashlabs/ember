/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What an event hands over, and who is handed it, and the files a template hands every event made
 * from it.
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
     * Refuses a file too large to travel to a partner station, before its bytes are read.
     *
     * <p>The channel between two instances speaks JSON, so a file crossing it is held whole in
     * memory several times over: the bytes, the text they are encoded as, and the body carrying
     * that text. The size a station accepts as an upload is the size it is willing to hand on, and
     * the question is asked of what the row says rather than of what has already been loaded.
     */
    public static void requireSizeToTravel(long fileSize, long limit) {
        if (fileSize > limit) throw EventRefusal.EVENT_FILE_TOO_LARGE_FOR_PARTNER.raise();
    }

    /**
     * The files of an event as this reader may have them.
     *
     * @param permissions the reader's permissions, already expanded
     */
    public List<EventAttachment> listFor(int eventId, Collection<StationPermission> permissions) {
        return visible(repository.findEffective(eventId), readsInternal(permissions));
    }

    /** Every file of an event or a template, for whoever is editing it. */
    public List<EventAttachment> listAll(AppointmentOwner owner, int ownerId) {
        return repository.findByOwner(owner, ownerId);
    }

    /** The files that leave the station: what an event hands the room, and never what it keeps back. */
    public List<EventAttachment> listOpen(int eventId) {
        return visible(repository.findEffective(eventId), false);
    }

    /**
     * The open files of several events at once, so a listing does not ask once per row.
     */
    public Map<Integer, List<EventAttachment>> listOpenFor(List<Integer> eventIds) {
        var byEvent = repository.findByEventIds(eventIds);
        byEvent.replaceAll((eventId, attachments) -> visible(attachments, false));
        return byEvent;
    }

    public Optional<EventAttachment> find(AppointmentOwner owner, int attachmentId) {
        return repository.findById(owner, attachmentId);
    }

    /**
     * A file an appointment hands over: its own, or for a part one of its event group's.
     *
     * @param eventId      the appointment
     * @param attachmentId the attachment
     * @return the attachment carrying the appointment's id, or empty where the appointment hands over no such file
     */
    public Optional<EventAttachment> findOn(int eventId, int attachmentId) {
        return repository.findEffective(eventId).stream()
                .filter(attachment -> attachment.id() == attachmentId)
                .findFirst();
    }

    /**
     * The file behind an attachment of an appointment, where this reader may be handed it.
     *
     * @param eventId     the appointment, whose event group's files count as its own
     * @param permissions the reader's permissions, already expanded
     * @return the attachment, or empty where there is none or it is kept back from this reader
     */
    public Optional<EventAttachment> findReadableOn(
            int eventId, int attachmentId, Collection<StationPermission> permissions) {
        return findOn(eventId, attachmentId).filter(attachment -> !attachment.internal() || readsInternal(permissions));
    }

    /**
     * The files an event group hands over on every part, as this reader may have them.
     *
     * @param permissions the reader's permissions, already expanded
     */
    public List<EventAttachment> listForGroup(int groupId, Collection<StationPermission> permissions) {
        return visible(repository.findByGroup(groupId), readsInternal(permissions));
    }

    /** Every file of an event group, for whoever is editing it. */
    public List<EventAttachment> listAllOfGroup(int groupId) {
        return repository.findByGroup(groupId);
    }

    /**
     * A file of an event group.
     *
     * @return the attachment, or empty where the event group hands over no such file
     */
    public Optional<EventAttachment> findOfGroup(int groupId, int attachmentId) {
        return repository.findGroupAttachment(attachmentId).filter(attachment -> attachment.eventId() == groupId);
    }

    /**
     * A file of an event group, where this reader may be handed it.
     *
     * @param permissions the reader's permissions, already expanded
     */
    public Optional<EventAttachment> findReadableOfGroup(
            int groupId, int attachmentId, Collection<StationPermission> permissions) {
        return findOfGroup(groupId, attachmentId)
                .filter(attachment -> !attachment.internal() || readsInternal(permissions));
    }

    /**
     * Attaches a file from the station's library to an event group, which hands it over on every part.
     * The file has to belong to the same station.
     */
    public EventAttachment attachToGroup(
            int groupId, int stationId, int fileId, @Nullable String label, boolean internal) {
        requireFileOf(stationId, fileId);
        var attachment = repository.attachToGroup(groupId, fileId, blankToNull(label), internal);
        log.info("File {} attached to event group {} in station {}", fileId, groupId, stationId);
        return attachment;
    }

    public boolean updateOfGroup(int attachmentId, @Nullable String label, boolean internal) {
        return repository.updateGroupAttachment(attachmentId, blankToNull(label), internal);
    }

    public void reorderGroup(int groupId, List<Integer> attachmentIds) {
        repository.reorderGroup(groupId, attachmentIds);
    }

    public boolean detachFromGroup(int attachmentId) {
        return repository.detachFromGroup(attachmentId);
    }

    private void requireFileOf(int stationId, int fileId) {
        var file = media.findFile(fileId).orElseThrow(EventRefusal.EVENT_FILE_TO_ATTACH_NOT_HERE::raise);
        if (!Objects.equals(file.stationId(), stationId)) {
            throw EventRefusal.EVENT_FILE_TO_ATTACH_NOT_HERE.raise();
        }
    }

    /**
     * Attaches a file from the station's library to an event or a template. The file has to belong
     * to the same station, because an attachment is a reference into that station's library and
     * nothing else.
     */
    public EventAttachment attach(
            AppointmentOwner owner, int ownerId, int stationId, int fileId, @Nullable String label, boolean internal) {
        requireFileOf(stationId, fileId);
        var attachment = repository.attach(owner, ownerId, fileId, blankToNull(label), internal);
        log.info(
                "File {} attached to {} {} in station {} ({})",
                fileId,
                owner,
                ownerId,
                stationId,
                internal ? "internal" : "open");
        return attachment;
    }

    public boolean update(AppointmentOwner owner, int attachmentId, @Nullable String label, boolean internal) {
        return repository.update(owner, attachmentId, blankToNull(label), internal);
    }

    public void reorder(AppointmentOwner owner, int ownerId, List<Integer> attachmentIds) {
        repository.reorder(owner, ownerId, attachmentIds);
    }

    public boolean detach(AppointmentOwner owner, int attachmentId) {
        return repository.detach(owner, attachmentId);
    }

    private static List<EventAttachment> visible(List<EventAttachment> attachments, boolean withInternal) {
        return attachments.stream()
                .filter(attachment -> withInternal || !attachment.internal())
                .toList();
    }

    private static @Nullable String blankToNull(@Nullable String label) {
        return label == null || label.isBlank() ? null : label;
    }
}
