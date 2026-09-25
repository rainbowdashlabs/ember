/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {pageModuleKeys, titleWithModule} from './pageModule'

/**
 * Which parts of the product a page is said to belong to, and how the tab reads them out.
 *
 * @vitest-environment happy-dom
 *
 * <p>The case worth holding is the association's, where one view is mounted at two addresses: the
 * station's appointment and the association's appointment are otherwise the same tab twice.
 */
describe('the part of the product a page belongs to', () => {
    it('names the section of a station page', () => {
        expect(pageModuleKeys('/station/boards/GEM/tickets/4')).toEqual(['pageModule.boards'])
    })

    it('names the section and then the area for an association page', () => {
        expect(pageModuleKeys('/cluster/events/7')).toEqual(['pageModule.events', 'pageModule.cluster'])
    })

    /** A partner's catalogue is a catalogue, and it is a partner's: the tab is allowed to say both. */
    it('names the section and then the area for a partner station page', () => {
        expect(pageModuleKeys('/station/federation/quiz/abc/3'))
            .toEqual(['pageModule.quiz', 'pageModule.federation'])
    })

    it('names the area alone where it has no section of its own', () => {
        expect(pageModuleKeys('/cluster/settings')).toEqual(['pageModule.cluster'])
    })

    /** Matching whole segments, so one section is not taken for another that starts the same way. */
    it('does not take one section for another whose name it begins with', () => {
        expect(pageModuleKeys('/station/newsletter')).toEqual([])
    })

    it('names nothing for a page standing outside the product', () => {
        expect(pageModuleKeys('/login')).toEqual([])
    })

    it('reads the page first and the product last', () => {
        expect(titleWithModule('GEM-4 Löschzug melden', ['Boards'])).toBe('GEM-4 Löschzug melden - Boards')
    })

    it('reads a section inside an area as both', () => {
        expect(titleWithModule('Sommerfest', ['Termine', 'Verband'])).toBe('Sommerfest - Termine - Verband')
    })

    /** A section's own front page is titled after the section, and saying it twice says nothing. */
    it('leaves out a part the title already says', () => {
        expect(titleWithModule('Boards', ['Boards'])).toBe('Boards')
    })

    /** A tab reading only the section name while the record loads is worse than one that waits. */
    it('says nothing at all until the page has a title', () => {
        expect(titleWithModule('', ['Boards'])).toBe('')
    })
})
