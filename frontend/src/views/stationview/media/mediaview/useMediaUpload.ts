/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {moveMediaFileToFolder, uploadMediaFile, type StationFileListing} from '@/api/media'
import {useAsyncAction} from '@/composables/useAsyncAction'

const UPLOAD_CONCURRENCY = 2

/**
 * Uploads dropped files into the open folder, a couple at a time, prepending each stored file to
 * the listing as soon as it lands.
 */
export function useMediaUpload(entries: Ref<StationFileListing[]>, activeFolder: Ref<number | null>) {
    const {t} = useI18n()
    const uploadError = ref<string | null>(null)

    const {running: uploading, run: uploadMany} = useAsyncAction(async (files: File[]) => {
        uploadError.value = null
        let failed = 0
        let cursor = 0
        const targetFolder = activeFolder.value

        async function worker() {
            while (cursor < files.length) {
                const f = files[cursor++]
                if (!f) continue
                try {
                    const stored = await uploadMediaFile(f)
                    const placed = targetFolder != null
                        ? (await moveMediaFileToFolder(stored.id, targetFolder), {...stored, folderId: targetFolder})
                        : stored
                    entries.value = [
                        {file: placed, inUse: false, tagIds: [], uploadedBy: null},
                        ...entries.value.filter(e => e.file.id !== placed.id),
                    ]
                } catch {
                    failed++
                }
            }
        }

        const workerCount = Math.min(UPLOAD_CONCURRENCY, files.length)
        await Promise.all(Array.from({length: workerCount}, () => worker()))

        if (failed > 0) uploadError.value = t('fileUpload.uploadFailed')
    })

    return {uploading, uploadError, uploadMany}
}
