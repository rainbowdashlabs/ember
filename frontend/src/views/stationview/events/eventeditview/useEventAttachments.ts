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
import {describeFailure, type Failure, type Translate} from '@/util/failure'

/**
 * The files an event hands over, while somebody is editing it.
 *
 * <p>Every change is written as it is made rather than gathered into the save: an event being
 * edited already exists, and a file that only lands when the form is saved is a file somebody
 * believes they attached. Picking one happens in the media library, so nothing is uploaded here.
 *
 * <p>A change that could not be written is said so and taken back off the screen. What the switch
 * shows is a claim about who may read the file, and a screen saying "kept back" over a file that is
 * still open to everyone is worse than no screen at all.
 *
 * <p>What went wrong is kept described rather than as the name of the step that failed. The name
 * of the step was never shown to anybody: the card drew one sentence for all five of them, so a
 * file refused because it is too large and a file refused because the session had run out read the
 * same. The translator is handed in rather than looked up so this stays callable from a test.
 *
 * @param eventId the event being edited, or null while it is being created
 * @param t       the translator, for describing whatever the server refuses with
 */
export function useEventAttachments(eventId: () => number | null, t: Translate) {
    const attachments = ref<EventAttachment[]>([])
    const loading = ref(false)
    const failure = ref<Failure | null>(null)

    async function load() {
        const id = eventId()
        if (id === null) return
        loading.value = true
        try {
            attachments.value = await events.listEventAttachments(id)
            failure.value = null
        } catch (e) {
            failure.value = describeFailure(e, t)
        } finally {
            loading.value = false
        }
    }

    /** Hangs a file of the library on the event, open to the room until somebody says otherwise. */
    async function add(file: StationFile) {
        const id = eventId()
        if (id === null) return
        try {
            attachments.value = [...attachments.value, await events.attachEventFile(id, file.id, null, false)]
            failure.value = null
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    async function save(attachment: EventAttachment) {
        const id = eventId()
        if (id === null) return
        try {
            await events.updateEventAttachment(id, attachment.id, attachment.label, attachment.internal)
            failure.value = null
        } catch (e) {
            const refused = describeFailure(e, t)
            await load()
            failure.value = refused
        }
    }

    async function remove(index: number) {
        const id = eventId()
        const attachment = attachments.value[index]
        if (id === null || !attachment) return
        try {
            await events.detachEventFile(id, attachment.id)
            attachments.value = attachments.value.filter((_, at) => at !== index)
            failure.value = null
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    async function reorder(fromIndex: number, toIndex: number) {
        const id = eventId()
        if (id === null) return
        const before = attachments.value
        attachments.value = moveWithin(attachments.value, fromIndex, toIndex)
        try {
            await events.reorderEventAttachments(id, attachments.value.map(attachment => attachment.id))
            failure.value = null
        } catch (e) {
            attachments.value = before
            failure.value = describeFailure(e, t)
        }
    }

    return {attachments, loading, failure, load, add, save, remove, reorder}
}
