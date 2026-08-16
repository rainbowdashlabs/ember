/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {KbAccessLevel, levelCovers} from './knowledgeBase'

/**
 * One permission decision the interface reuses everywhere an action is offered, so it has to answer
 * the same way the server's own check does.
 */
describe('levelCovers', () => {
    it('accepts an equal or higher level', () => {
        expect(levelCovers(KbAccessLevel.MANAGE, KbAccessLevel.WRITE)).toBe(true)
        expect(levelCovers(KbAccessLevel.WRITE, KbAccessLevel.WRITE)).toBe(true)
        expect(levelCovers(KbAccessLevel.READ, KbAccessLevel.READ)).toBe(true)
    })

    it('refuses a lower level', () => {
        expect(levelCovers(KbAccessLevel.READ, KbAccessLevel.WRITE)).toBe(false)
        expect(levelCovers(KbAccessLevel.WRITE, KbAccessLevel.MANAGE)).toBe(false)
    })

    it('refuses everything at no access', () => {
        expect(levelCovers(KbAccessLevel.NONE, KbAccessLevel.READ)).toBe(false)
        expect(levelCovers(KbAccessLevel.NONE, KbAccessLevel.MANAGE)).toBe(false)
    })

    it('treats an unreported level as no opinion', () => {
        expect(levelCovers(undefined, KbAccessLevel.MANAGE)).toBe(true)
    })
})
