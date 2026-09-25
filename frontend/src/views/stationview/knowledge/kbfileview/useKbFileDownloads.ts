/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {knowledgeBase} from '@/api'
import type {KbFile} from '@/api/knowledgeBase'
import {downloadAuthed} from '@/util/downloadAuthed'

/**
 * Taking a copy of a wiki entry away with you, as it was uploaded or rendered as a sheet.
 *
 * <p>Both go through the reader's session rather than a plain link, because the addresses behind
 * them answer nobody else, and both ask a different address for a partner's entry than for the
 * station's own. Which of the two is offered is the page's business; this only knows how to fetch.
 */
export function useKbFileDownloads(file: Ref<KbFile | null>, partnerStationUid: Ref<string | undefined>) {
    async function downloadOriginal() {
        if (!file.value) return
        await downloadAuthed(knowledgeBase.originalFileUrl(file.value.id), file.value.name)
    }

    async function downloadPdf() {
        if (!file.value) return
        const url = partnerStationUid.value
            ? knowledgeBase.federatedPdfExportUrl(partnerStationUid.value, file.value.id)
            : knowledgeBase.pdfExportUrl(file.value.id)
        await downloadAuthed(url, `${file.value.name}.pdf`)
    }

    return {downloadOriginal, downloadPdf}
}
