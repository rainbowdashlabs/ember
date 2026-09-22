/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ResultDimension, ResultMatch} from '@/api/forms'
import {decodeView, DEFAULT_AGE_BOUNDS, emptyFilter, encodeView, filterActive, groupingBy, parseBounds, toQuery} from './resultQuery'

/** What the results view asks for, and how that choice survives a trip through the address bar. */
describe('resultQuery', () => {
    it('asks for nothing while neither a filter nor a grouping is set', () => {
        expect(toQuery(emptyFilter(), null)).toBeNull()
        expect(encodeView(emptyFilter(), null)).toBeUndefined()
    })

    it('leaves an empty filter out when only grouping', () => {
        const query = toQuery(emptyFilter(), groupingBy(ResultDimension.GROUP))

        expect(query?.filter).toBeNull()
        expect(query?.groupBy?.by).toBe(ResultDimension.GROUP)
    })

    it('counts any chosen attribute as an active filter', () => {
        expect(filterActive({...emptyFilter(), groupIds: [1]})).toBe(true)
        expect(filterActive({...emptyFilter(), ageTo: 17})).toBe(true)
        expect(filterActive(emptyFilter())).toBe(false)
    })

    it('starts an age grouping on the default brackets', () => {
        expect(groupingBy(ResultDimension.AGE).bounds).toEqual(DEFAULT_AGE_BOUNDS)
        expect(groupingBy(ResultDimension.TAG).bounds).toEqual([])
    })

    it('reads bracket starts the way a reader types them', () => {
        expect(parseBounds('18, 14 27;14 x')).toEqual([14, 18, 27])
    })

    it('comes back from the address bar as it went in', () => {
        const filter = {...emptyFilter(), groupIds: [3, 4], groupMatch: ResultMatch.ALL}
        const grouping = {...groupingBy(ResultDimension.GROUP), only: ['3', '4']}

        const view = decodeView(encodeView(filter, grouping))

        expect(view.filter).toEqual(filter)
        expect(view.grouping).toEqual(grouping)
    })

    it('falls back to the plain view for anything unreadable', () => {
        expect(decodeView('not json')).toEqual({filter: emptyFilter(), grouping: null})
        expect(decodeView(JSON.stringify({grouping: {by: 'NONSENSE'}})).grouping).toBeNull()
        expect(decodeView(undefined).grouping).toBeNull()
    })
})
