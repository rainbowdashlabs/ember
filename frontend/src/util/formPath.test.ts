/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {followingPage, longestFrom, reachablePages, walkPath, type PathAnswers, type PathPage, type PathQuestion} from './formPath'
import fixtures from './formPath.fixtures.json'

/** One example form and the path its answers must take, shared with the server's walk. */
interface Fixture {
    name: string
    pages: PathPage[]
    questions: PathQuestion[]
    answers: PathAnswers
    path: string[]
}

const next = {kind: 'NEXT' as const}
const to = (page: string) => ({kind: 'PAGE' as const, page})

function pages(...afters: PathPage['after'][]): PathPage[] {
    return afters.map((after, index) => ({key: `p${index}`, after}))
}

/**
 * The walk through a form's pages. The example forms are the ones the server walks in its own test,
 * so the browser and the server cannot come to different paths.
 */
describe('formPath', () => {
    for (const fixture of fixtures as unknown as Fixture[]) {
        it(fixture.name, () => {
            expect(walkPath(fixture.pages, fixture.questions, fixture.answers)).toEqual(fixture.path)
        })
    }

    it('says where a page leads before the reader answers anything', () => {
        expect(followingPage(pages(next, next), [], {}, 'p0')).toBe('p1')
        expect(followingPage(pages(next, next), [], {}, 'p1')).toBeNull()
    })

    it('measures the longest path still possible, every branch included', () => {
        const branching: PathQuestion[] = [{id: 1, pageKey: 'p0', branch: {a: to('p1')}}]
        const form = pages(to('p2'), next, next)
        expect(longestFrom(form, [], 'p0')).toBe(2)
        expect(longestFrom(form, branching, 'p0')).toBe(3)
        expect(longestFrom(form, branching, 'p2')).toBe(1)
    })

    it('finds the pages no path reaches', () => {
        const form = pages(to('p2'), next, next)
        expect([...reachablePages(form, [])]).toEqual(['p0', 'p2'])
        expect([...reachablePages(form, [{id: 1, pageKey: 'p0', branch: {a: to('p1')}}])].sort())
            .toEqual(['p0', 'p1', 'p2'])
    })
})
