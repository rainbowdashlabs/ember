/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {events} from '@/api'
import type {EventAttachment} from '@/api/events'
import type {StationFile} from '@/api/media'
import {moveWithin} from '@/util/reorder'

/**
 * The files an event hands over, while somebody is editing it.
 *
 * <p>Every change is written as it is made rather than gathered into the save: an event being
 * edited already exists, and a file that only lands when the form is saved is a file somebody
 * believes they attached. Picking one happens in the media library, so nothing is uploaded here.
 */
export function useEventAttachments(eventId: () => number | null) {
    const attachments = ref<EventAttachment[]>([])
    const loading = ref(false)
    const error = ref('')

    async function load() {
        const id = eventId()
        if (id === null) return
        loading.value = true
        try {
            attachments.value = await events.listEventAttachments(id)
        } catch {
            error.value = 'load'
        } finally {
            loading.value = false
        }
    }

    /** Hangs a file of the library on the event, open to the room until somebody says otherwise. */
    async function add(file: StationFile) {
        const id = eventId()
        if (id === null) return
        attachments.value = [...attachments.value, await events.attachEventFile(id, file.id, null, false)]
    }

    async function save(attachment: EventAttachment) {
        const id = eventId()
        if (id === null) return
        await events.updateEventAttachment(id, attachment.id, attachment.label, attachment.internal)
    }

    async function remove(index: number) {
        const id = eventId()
        const attachment = attachments.value[index]
        if (id === null || !attachment) return
        await events.detachEventFile(id, attachment.id)
        attachments.value = attachments.value.filter((_, at) => at !== index)
    }

    async function reorder(fromIndex: number, toIndex: number) {
        const id = eventId()
        if (id === null) return
        attachments.value = moveWithin(attachments.value, fromIndex, toIndex)
        await events.reorderEventAttachments(id, attachments.value.map(attachment => attachment.id))
    }

    return {attachments, loading, error, load, add, save, remove, reorder}
}
