/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {KbAccessLevel, KbFileType, levelCovers, rawFileUrl} from './knowledgeBase'

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

/**
 * A file taken away from a tile has to be the file that was uploaded, not what it is shown as: a
 * presentation is shown as its converted PDF, and taking that instead would lose the slides.
 */
describe('rawFileUrl', () => {
    it('fetches a presentation as the file that was uploaded', () => {
        expect(rawFileUrl({id: 7, fileType: KbFileType.PRESENTATION})).toBe('/kb/files/7/original')
    })

    it('fetches everything else as its content, which is the file itself', () => {
        expect(rawFileUrl({id: 7, fileType: KbFileType.PDF})).toBe('/kb/files/7/content')
        expect(rawFileUrl({id: 7, fileType: KbFileType.IMAGE})).toBe('/kb/files/7/content')
    })
})
