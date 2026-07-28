/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {getItem} from '@/api/storage'
import type {KbFolder} from '@/api/knowledgeBase'
import {useFlashMessage} from '@/composables/useFlashMessage'

/**
 * Copies the public link of the currently browsed folder to the clipboard and
 * reports the short-lived "copied" state to the breadcrumb.
 */
export function useKbShareLink(currentFolder: Ref<KbFolder | null>) {
    const {message, flash} = useFlashMessage(2000)

    const shareCopied = computed(() => message.value !== '')

    function copyShareLink() {
        const stationUid = getItem('station_id') ?? ''
        const folderId = currentFolder.value?.id
        const url = folderId
            ? `${window.location.origin}/public/kb/${stationUid}?folderId=${folderId}`
            : `${window.location.origin}/public/kb/${stationUid}`
        navigator.clipboard.writeText(url).then(() => flash(url))
    }

    return {shareCopied, copyShareLink}
}
