/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, describe, expect, it} from 'vitest'
import {canCoverAutomatically, coversForPasswords, coversForPersonalData, pageMapping} from './pictureCovers'

/**
 * The covers that are drawn for somebody rather than by them.
 *
 * <p>What matters most here is when they are *not* offered. A picture of a whole screen carries the
 * browser's own furniture, and there is no way from a page to tell how much of it: boxes calculated
 * against it land beside what they were meant to cover, and a cover that misses gives the comfort of
 * one without the effect.
 */
describe('pictureCovers', () => {
    const width = 1200
    const height = 800

    function sizeWindow() {
        Object.defineProperty(window, 'innerWidth', {value: width, configurable: true})
        Object.defineProperty(window, 'innerHeight', {value: height, configurable: true})
    }

    afterEach(() => {
        document.body.innerHTML = ''
    })

    it('reads one page pixel against the picture where the two are the same shape', () => {
        sizeWindow()

        expect(pageMapping({width: 2400, height: 1600})).toBe(2)
        expect(pageMapping({width: 1200, height: 800})).toBe(1)
    })

    /** A whole screen or a window with browser furniture on it is a different shape. */
    it('offers nothing where the picture is not of the page alone', () => {
        sizeWindow()

        expect(pageMapping({width: 1920, height: 1080})).toBeNull()
        expect(canCoverAutomatically({width: 1920, height: 1080})).toBe(false)
        expect(coversForPersonalData({width: 1920, height: 1080})).toEqual([])
    })

    it('covers what the page marks as a name, an answer or an address', () => {
        sizeWindow()
        document.body.innerHTML = `
            <span data-testid="member-name">Nora</span>
            <div data-field="Geburtsdatum">03.11.2019</div>
            <a href="mailto:nora@example.com">schreiben</a>
            <p>nothing of anybody's</p>`
        for (const element of Array.from(document.querySelectorAll('span, div, a'))) {
            element.getBoundingClientRect = () => ({
                left: 10, top: 20, width: 100, height: 16, right: 110, bottom: 36, x: 10, y: 20, toJSON: () => ({}),
            })
        }

        const covers = coversForPersonalData({width, height})

        expect(covers).toHaveLength(3)
        expect(covers[0]).toMatchObject({x: 10, y: 20, width: 100, height: 16})
    })

    /** The one covering nobody is asked about, because no report needs the characters of a password. */
    it('covers a password field without being asked', () => {
        sizeWindow()
        document.body.innerHTML = '<input type="password" value="hunter2"/>'
        const field = document.querySelector('input')!
        field.getBoundingClientRect = () => ({
            left: 4, top: 8, width: 200, height: 24, right: 204, bottom: 32, x: 4, y: 8, toJSON: () => ({}),
        })

        expect(coversForPasswords({width, height})).toHaveLength(1)
    })
})
