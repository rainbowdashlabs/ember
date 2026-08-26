/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {news} from '@/api'
import type {NewsAttachment} from '@/api/news'
import type {StationFile} from '@/api/media'
import {moveWithin} from '@/util/reorder'

/**
 * One attachment as the editor holds it. `id` is null while the author has picked a file but the
 * entry has not been saved yet, which is what lets a brand-new entry carry attachments before it
 * has an id of its own.
 */
export interface AttachmentDraft {
    id: number | null
    fileId: number
    label: string
    fileName: string
    mimeType: string
    fileSize: number
    contentHash: string
}

function toDraft(attachment: NewsAttachment): AttachmentDraft {
    return {
        id: attachment.id,
        fileId: attachment.fileId,
        label: attachment.label ?? '',
        fileName: attachment.fileName,
        mimeType: attachment.mimeType,
        fileSize: attachment.fileSize,
        contentHash: attachment.contentHash,
    }
}

/**
 * The attachments of the entry being edited, and how they get written back.
 *
 * <p>Attachments hang off the entry rather than its body, so nothing here touches the markdown.
 * They are reconciled once, after the entry itself is saved: what the author removed is detached,
 * what they picked is attached, renamed labels are written, and the order they arranged is
 * recorded last.
 */
export function useNewsAttachments() {
    const attachments = ref<AttachmentDraft[]>([])
    const originals = ref<NewsAttachment[]>([])

    function load(existing: NewsAttachment[]) {
        originals.value = existing
        attachments.value = existing.map(toDraft)
    }

    function add(file: StationFile) {
        if (!file.contentHash) return
        if (attachments.value.some(a => a.fileId === file.id)) return
        attachments.value = [...attachments.value, {
            id: null,
            fileId: file.id,
            label: '',
            fileName: file.fileName,
            mimeType: file.mimeType,
            fileSize: file.fileSize,
            contentHash: file.contentHash,
        }]
    }

    function remove(index: number) {
        attachments.value = attachments.value.filter((_, i) => i !== index)
    }

    function reorder(fromIndex: number, toIndex: number) {
        attachments.value = moveWithin(attachments.value, fromIndex, toIndex)
    }

    async function persist(newsId: number) {
        const kept = new Set(attachments.value.map(a => a.id).filter((id): id is number => id !== null))
        for (const original of originals.value) {
            if (!kept.has(original.id)) await news.detachNewsAttachment(original.id)
        }

        const ids: number[] = []
        for (const draft of attachments.value) {
            if (draft.id === null) {
                const created = await news.attachNewsFile(newsId, draft.fileId, draft.label || null)
                draft.id = created.id
                ids.push(created.id)
                continue
            }
            const original = originals.value.find(o => o.id === draft.id)
            if (original && (original.label ?? '') !== draft.label) {
                await news.relabelNewsAttachment(draft.id, draft.label || null)
            }
            ids.push(draft.id)
        }
        if (ids.length > 0) await news.reorderNewsAttachments(newsId, ids)
        originals.value = attachments.value.map(a => ({
            id: a.id ?? 0,
            newsId,
            fileId: a.fileId,
            label: a.label || null,
            sortOrder: 0,
            createdAt: '',
            fileName: a.fileName,
            mimeType: a.mimeType,
            fileSize: a.fileSize,
            contentHash: a.contentHash,
        }))
    }

    return {attachments, load, add, remove, reorder, persist}
}
