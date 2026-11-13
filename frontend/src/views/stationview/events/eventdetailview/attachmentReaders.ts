/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {eventGroups, events} from '@/api'
import type {EventAttachment} from '@/api/generated/schema'

/** Where the files a page hands over are read and fetched from. */
export interface AttachmentReader {
    list(): Promise<EventAttachment[]>
    fileUrl(attachmentId: number): string
    /** Where the picture of a file is fetched from, or empty where the owner offers none. */
    pictureUrl(attachmentId: number, width: number): string
    /** Whether the list mixes an event group's files with an appointment's, which are then marked. */
    showsEventGroupFiles: boolean
}

/** The files of an appointment, those of its event group first; all fetched through the appointment. */
export function eventAttachmentReader(eventId: number): AttachmentReader {
    return {
        list: () => events.listEventAttachments(eventId),
        fileUrl: attachmentId => events.eventAttachmentUrl(eventId, attachmentId),
        pictureUrl: (attachmentId, width) => events.eventAttachmentPictureUrl(eventId, attachmentId, width),
        showsEventGroupFiles: true,
    }
}

/** The files an event group hands over on every part, read on the event group's page. */
export function eventGroupAttachmentReader(eventGroupId: number): AttachmentReader {
    return {
        list: () => eventGroups.listAttachments(eventGroupId),
        fileUrl: attachmentId => eventGroups.attachmentUrl(eventGroupId, attachmentId),
        pictureUrl: () => '',
        showsEventGroupFiles: false,
    }
}
