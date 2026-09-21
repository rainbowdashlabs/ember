/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {knowledgeBase} from '@/api'
import {KbFavouriteTarget, type FavouriteEntry, type KbFavourite} from '@/api/knowledgeBase'

/**
 * How two favourites are told apart: by what they point at, with the partner as part of it for a
 * partner's entry, since an id is only unique on the station that owns it.
 */
export function favouriteKey(entry: FavouriteEntry): string {
    const partner = entry.target === KbFavouriteTarget.PARTNER_FILE
        || entry.target === KbFavouriteTarget.PARTNER_FOLDER
    return partner
        ? `${entry.target}:${entry.partnerStationUid}:${entry.entryId}`
        : `${entry.target}:${entry.entryId}`
}

/**
 * The reader's favourites in the wiki, for a page that draws stars and a favourites folder.
 *
 * <p>Fetched by each page that asks rather than held for the whole session: favourites belong to a
 * member of one station, and a list kept across a change of station would star the wrong entries.
 * Marking and unmarking change the list in place, so a page never fetches it again for its own
 * changes.
 */
export function useKbFavourites() {
    const list = ref<KbFavourite[]>([])
    const byKey = computed(() => new Map(list.value.map(favourite => [favouriteKey(favourite), favourite])))

    async function load() {
        list.value = await knowledgeBase.listFavourites()
    }

    function isFavourite(entry: FavouriteEntry): boolean {
        return byKey.value.has(favouriteKey(entry))
    }

    /** Marks what is not a favourite yet and unmarks what is. Fails as the request fails. */
    async function toggle(entry: FavouriteEntry) {
        const existing = byKey.value.get(favouriteKey(entry))
        if (existing) {
            await knowledgeBase.unmarkFavourite(existing.id)
            list.value = list.value.filter(favourite => favourite.id !== existing.id)
            return
        }
        const marked = await knowledgeBase.markFavourite(entry)
        list.value = [marked, ...list.value]
    }

    return {
        favourites: computed(() => list.value),
        load,
        isFavourite,
        toggle,
    }
}

export type KbFavourites = ReturnType<typeof useKbFavourites>
