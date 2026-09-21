/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {KbFavouriteTarget, type FavouriteEntry} from '@/api/knowledgeBase'
import {useKbFavourites} from '@/composables/useKbFavourites'

/**
 * Whether the file being read is one of the reader's favourites, and the star that changes it.
 *
 * <p>The entry is named from the address alone, this station's file or a partner's, so the star is
 * right before the file has loaded and stays usable when a partner's file does not load at all,
 * which is exactly when a reader wants to take a favourite off.
 *
 * @param fileId            the file being read
 * @param partnerStationUid the partner serving it, when it is a partner's
 * @param error             where a failure is reported, the page's own error line
 */
export function useKbFileFavourite(
    fileId: () => number,
    partnerStationUid: () => string | undefined,
    error: Ref<string>,
) {
    const {t} = useI18n()
    const favourites = useKbFavourites()

    const entry = computed<FavouriteEntry>(() => {
        const partner = partnerStationUid()
        return partner
            ? {target: KbFavouriteTarget.PARTNER_FILE, entryId: fileId(), partnerStationUid: partner}
            : {target: KbFavouriteTarget.FILE, entryId: fileId()}
    })

    const marked = computed(() => favourites.isFavourite(entry.value))

    async function load() {
        try {
            await favourites.load()
        } catch {
            error.value = t('common.error')
        }
    }

    async function toggle() {
        try {
            await favourites.toggle(entry.value)
        } catch {
            error.value = t('kb.favouriteUnavailable')
        }
    }

    return {marked, load, toggle}
}
