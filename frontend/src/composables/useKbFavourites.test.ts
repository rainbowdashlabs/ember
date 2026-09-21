/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {KbFavouriteTarget, type KbFavourite} from '@/api/knowledgeBase'
import {favouriteKey, useKbFavourites} from './useKbFavourites'

const listFavourites = vi.fn()
const markFavourite = vi.fn()
const unmarkFavourite = vi.fn()

vi.mock('@/api', () => ({
    knowledgeBase: {
        listFavourites: () => listFavourites(),
        markFavourite: (entry: unknown) => markFavourite(entry),
        unmarkFavourite: (id: number) => unmarkFavourite(id),
    },
}))

const PARTNER = '00000000-0000-4000-a000-0000000000f1'

function favourite(overrides: Partial<KbFavourite>): KbFavourite {
    return {
        id: 1,
        target: KbFavouriteTarget.FILE,
        entryId: 7,
        partnerStationUid: null,
        title: 'Hydrantenplan',
        fileType: 'PDF',
        stationName: null,
        createdAt: '2026-09-21T10:00:00Z',
        ...overrides,
    }
}

/**
 * The favourites a page draws its stars from, told apart the way the server tells them apart and
 * changed in place, so the page never fetches the list again for its own changes.
 */
describe('useKbFavourites', () => {
    beforeEach(() => {
        listFavourites.mockReset()
        markFavourite.mockReset()
        unmarkFavourite.mockReset()
    })

    it('knows a loaded favourite by what it points at', async () => {
        listFavourites.mockResolvedValue([favourite({})])
        const favourites = useKbFavourites()

        await favourites.load()

        expect(favourites.isFavourite({target: KbFavouriteTarget.FILE, entryId: 7})).toBe(true)
        expect(favourites.isFavourite({target: KbFavouriteTarget.FOLDER, entryId: 7})).toBe(false)
    })

    /** An id is only unique on the station that owns it, so the partner is part of the name. */
    it('tells two partners entries with the same id apart', () => {
        const one = {target: KbFavouriteTarget.PARTNER_FILE, entryId: 7, partnerStationUid: PARTNER}
        const other = {...one, partnerStationUid: '00000000-0000-4000-a000-0000000000f2'}

        expect(favouriteKey(one)).not.toBe(favouriteKey(other))
    })

    it('marks what is not a favourite and keeps it without fetching the list again', async () => {
        listFavourites.mockResolvedValue([])
        markFavourite.mockResolvedValue(favourite({id: 5, target: KbFavouriteTarget.FOLDER, entryId: 3}))
        const favourites = useKbFavourites()
        await favourites.load()

        await favourites.toggle({target: KbFavouriteTarget.FOLDER, entryId: 3})

        expect(favourites.isFavourite({target: KbFavouriteTarget.FOLDER, entryId: 3})).toBe(true)
        expect(listFavourites).toHaveBeenCalledOnce()
    })

    it('takes the mark off what is a favourite, by the favourite it is', async () => {
        listFavourites.mockResolvedValue([favourite({id: 9})])
        unmarkFavourite.mockResolvedValue(undefined)
        const favourites = useKbFavourites()
        await favourites.load()

        await favourites.toggle({target: KbFavouriteTarget.FILE, entryId: 7})

        expect(unmarkFavourite).toHaveBeenCalledWith(9)
        expect(favourites.favourites.value).toEqual([])
    })

    it('leaves the list as it was when the server refuses', async () => {
        listFavourites.mockResolvedValue([])
        markFavourite.mockRejectedValue(new Error('not shared'))
        const favourites = useKbFavourites()
        await favourites.load()

        await expect(favourites.toggle({
            target: KbFavouriteTarget.PARTNER_FILE, entryId: 4, partnerStationUid: PARTNER,
        })).rejects.toThrow()
        expect(favourites.favourites.value).toEqual([])
    })
})
