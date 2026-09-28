/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {followingPage, longestFrom, reachablePages, walkPath, type PathPage} from './formPath'

const next = {kind: 'NEXT' as const}
const submit = {kind: 'SUBMIT' as const}
const to = (page: string) => ({kind: 'PAGE' as const, page})

function pages(...afters: PathPage['after'][]): PathPage[] {
    return afters.map((after, index) => ({key: `p${index}`, after}))
}

/** A form's pages lead on to the page below, to a chosen page further down, or to the end. */
describe('formPath', () => {
    it('goes through every page of a form that leads on page by page', () => {
        expect(walkPath(pages(next, next, next))).toEqual(['p0', 'p1', 'p2'])
    })

    it('skips to a chosen page', () => {
        expect(walkPath(pages(to('p2'), next, next))).toEqual(['p0', 'p2'])
    })

    it('ends where a page sends the form', () => {
        expect(walkPath(pages(submit, next))).toEqual(['p0'])
        expect(followingPage(pages(submit, next), 'p0')).toBeNull()
    })

    it('never follows a page that leads upwards', () => {
        expect(walkPath(pages(next, to('p0')))).toEqual(['p0', 'p1'])
    })

    it('measures the longest path still possible', () => {
        const form = pages(to('p2'), next, next)
        expect(longestFrom(form, 'p0')).toBe(2)
        expect(longestFrom(form, 'p1')).toBe(2)
        expect(longestFrom(form, 'p2')).toBe(1)
    })

    it('finds the pages no path reaches', () => {
        expect([...reachablePages(pages(to('p2'), next, next))]).toEqual(['p0', 'p2'])
    })
})
