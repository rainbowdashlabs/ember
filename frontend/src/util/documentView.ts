/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {fileKindOf} from '@/util/fileKind'
import {isHandheld} from '@/util/handheld'
import {saveBlob} from '@/util/downloadAuthed'
import type {DocumentFile} from '@/util/documentFile'

/** A document the reader is looking at, held until they close it. */
export interface ViewedDocument {
    blob: Blob
    filename: string
    mimeType: string
}

const viewed = ref<ViewedDocument | null>(null)

/**
 * Hands a finished document to the reader, by whichever route that device actually has.
 *
 * <p>At a desk the answer is the one everybody expects: the file is saved, and the reader opens it
 * from wherever their browser puts such things. In the hand there is no such place, and asking the
 * browser to make one is what left a reader staring at an empty tab where a report should have been.
 * So the document is opened where it already is, in the page that built it, and the button to save it
 * stays there for anybody who wants it saved anyway.
 *
 * <p>A document nothing can draw, an archive of them for instance, is saved at either size, because
 * showing it is not on offer and pretending otherwise would only cost the reader a press.
 */
export function presentDocument(document: DocumentFile) {
    const {blob, filename} = document
    const mimeType = blob.type
    if (isHandheld() && fileKindOf(mimeType) !== 'other') {
        viewed.value = {blob, filename, mimeType}
        return
    }
    saveBlob(blob, filename)
}

/** Closes whatever is being read, which is what releases its bytes. */
export function closeDocument() {
    viewed.value = null
}

/** Read-only view of the document on screen; the host renders against this. */
export function getViewedDocument() {
    return readonly(viewed)
}
