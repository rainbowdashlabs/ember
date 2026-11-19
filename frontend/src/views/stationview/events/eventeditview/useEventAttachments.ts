/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {EventAttachment, StationFile} from '@/api/generated/schema'
import {moveWithin} from '@/util/reorder'
import {describeFailure, type Failure, type Translate} from '@/util/failure'
import {showToast} from '@/util/toast'
import type {AttachmentPort} from './eventOwnerPorts'

/**
 * The files an event or an event template hands over, while somebody is editing it.
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
 * <p>Whose files they are comes in as a port, fixed to one owner. A template's rows carry the
 * template's id where an event's carry the event's, so nothing here reads an owner off a row.
 *
 * <p>While the owner does not exist yet, a new appointment or event group, the files are kept here
 * as pending: they can be added, labelled, marked internal, put in order and removed like any other,
 * and {@link attachPendingTo} hangs them on the owner in their order once it has been created. A
 * pending file carries a negative id, which no stored attachment has.
 *
 * @param port the files of the owner being edited, or null while it is being created
 * @param t    the translator, for describing whatever the server refuses with
 */
export function useEventAttachments(port: () => AttachmentPort | null, t: Translate) {
    const attachments = ref<EventAttachment[]>([])
    let nextPendingId = -1

    /** A file of the library as it stands in the list until its owner exists. */
    function pendingOf(file: StationFile): EventAttachment {
        return {
            id: nextPendingId--, eventId: 0, fileId: file.id, label: null, internal: false,
            sortOrder: attachments.value.length, createdAt: new Date().toISOString(), fileName: file.fileName,
            mimeType: file.mimeType, fileSize: file.fileSize, contentHash: file.contentHash, fromEventGroup: false,
        }
    }
    const loading = ref(false)
    const failure = ref<Failure | null>(null)

    async function load() {
        const owner = port()
        if (owner === null) return
        loading.value = true
        try {
            attachments.value = await owner.list()
            failure.value = null
        } catch (e) {
            failure.value = describeFailure(e, t)
        } finally {
            loading.value = false
        }
    }

    /** Hangs a file of the library on the owner, open to the room until somebody says otherwise. */
    async function add(file: StationFile) {
        const owner = port()
        if (owner === null) {
            attachments.value = [...attachments.value, pendingOf(file)]
            return
        }
        try {
            attachments.value = [...attachments.value, await owner.attach(file.id, null, false)]
            failure.value = null
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    async function save(attachment: EventAttachment) {
        const owner = port()
        if (owner === null) {
            attachments.value = attachments.value.map(entry => entry.id === attachment.id ? {...attachment} : entry)
            return
        }
        try {
            await owner.update(attachment.id, attachment.label, attachment.internal)
            failure.value = null
        } catch (e) {
            const refused = describeFailure(e, t)
            await load()
            failure.value = refused
        }
    }

    async function remove(index: number) {
        const owner = port()
        const attachment = attachments.value[index]
        if (!attachment) return
        if (owner === null) {
            attachments.value = attachments.value.filter((_, at) => at !== index)
            return
        }
        try {
            await owner.detach(attachment.id)
            attachments.value = attachments.value.filter((_, at) => at !== index)
            failure.value = null
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    async function reorder(fromIndex: number, toIndex: number) {
        const owner = port()
        if (owner === null) {
            attachments.value = moveWithin(attachments.value, fromIndex, toIndex)
            return
        }
        const before = attachments.value
        attachments.value = moveWithin(attachments.value, fromIndex, toIndex)
        try {
            await owner.reorder(attachments.value.map(attachment => attachment.id))
            failure.value = null
        } catch (e) {
            attachments.value = before
            failure.value = describeFailure(e, t)
        }
    }

    /**
     * Hangs the pending files on the owner just created, in their order, with their labels and flags.
     * The owner exists by now, so a file that cannot be attached does not undo it: it is left out, and a
     * message names the files that are missing so they can be attached again in its editor.
     *
     * A pending file the owner already received another way, from a template or from the appointment it
     * was copied from, is dropped rather than attached a second time.
     *
     * @param target    the files of the owner just created
     * @param carried   the library files the owner already holds
     * @return the names of the files that could not be attached
     */
    async function attachPendingTo(target: AttachmentPort, carried: ReadonlySet<number> = new Set()): Promise<string[]> {
        const failed: string[] = []
        const attached: EventAttachment[] = []
        for (const file of attachments.value.filter(entry => entry.id < 0 && !carried.has(entry.fileId))) {
            try {
                attached.push(await target.attach(file.fileId, file.label, file.internal))
            } catch {
                failed.push(file.label || file.fileName)
            }
        }
        attachments.value = [...attachments.value.filter(entry => entry.id >= 0), ...attached]
        if (failed.length > 0) showToast(t('events.attachments.pendingFailed', {files: failed.join(', ')}), 'error')
        return failed
    }

    /**
     * Hangs files of another owner, such as a template's, on the owner being edited, in their order and
     * with their labels, and reads the list back. A library file the owner already carries is left as
     * it is. The server only links a template's files when it creates an appointment, so an existing one
     * gets them here.
     *
     * @param files the files to hand over
     */
    async function attachMissing(files: readonly EventAttachment[]) {
        const owner = port()
        if (owner === null) return
        const carried = new Set(attachments.value.map(attachment => attachment.fileId))
        for (const file of files.filter(entry => !carried.has(entry.fileId))) {
            await owner.attach(file.fileId, file.label, file.internal)
        }
        await load()
    }

    return {attachments, loading, failure, load, add, save, remove, reorder, attachPendingTo, attachMissing}
}
