/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref, shallowReadonly} from 'vue'
import {canBeRead} from '@/util/fileKind'
import {isHandheld} from '@/util/handheld'
import {SaveResult, saveBlob} from '@/util/saveBlob'

/** A document the reader is looking at, held until they close it. */
export interface ViewedDocument {
    blob: Blob
    filename: string
    mimeType: string
}

const viewed = ref<ViewedDocument | null>(null)
const unsaved = ref<string | null>(null)

/**
 * Hands a finished document to the reader, by whichever route that device actually has.
 *
 * <p>At a desk the answer is the one everybody expects: the file is saved, and the reader opens it
 * from wherever their browser puts such things. In the hand there is no such place, and asking the
 * browser to make one is what left a reader staring at an empty tab where a report should have been.
 * So the document is opened where it already is, in the page that built it, and the button to save it
 * stays there for anybody who wants it saved anyway.
 *
 * <p>Only what there is a viewer for is opened: a picture, a document with pages, a recording. A
 * list of values or an archive is saved at either size, because showing a reader its bytes as words
 * is not showing them the thing they asked for, and it costs them a press to get out of. What a
 * file is gets read from its name as well as its type, since one a member uploaded often carries no
 * type worth the name and would otherwise be taken for something nobody can draw.
 */
export async function presentDocument(blob: Blob, filename: string, mimeType = blob.type): Promise<void> {
    if (isHandheld() && canBeRead(mimeType, filename)) {
        viewed.value = {blob, filename, mimeType}
        return
    }
    if (await saveBlob(blob, filename) === SaveResult.UNAVAILABLE) unsaved.value = filename
}

/**
 * The file this device could neither show nor save, waiting to be reported once.
 *
 * <p>Held here rather than said here, because what to say is a sentence in a language and this knows
 * nothing about either. The host beside the toasts reads it, tells the reader and clears it.
 */
export function getUnsavedDocument() {
    return readonly(unsaved)
}

/** Forgets a reported failure, so the next one is reported in its turn. */
export function clearUnsavedDocument() {
    unsaved.value = null
}

/** Closes whatever is being read, which is what releases its bytes. */
export function closeDocument() {
    viewed.value = null
}

/**
 * Read-only view of the document on screen; the host renders against this.
 *
 * <p>Shallow, so that the bytes stay a Blob. Made deeply read-only they become a shape that merely
 * resembles one, and every reader of them has to swear to the compiler that it is a Blob after all.
 */
export function getViewedDocument() {
    return shallowReadonly(viewed)
}
