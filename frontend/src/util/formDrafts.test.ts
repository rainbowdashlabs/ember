/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it} from 'vitest'
import {clearFormDraft, readFormDraft, saveFormDraft} from './formDrafts'

/**
 * A half-filled public form stays in this browser, and only where the visitor allowed storage for
 * features.
 *
 * @vitest-environment happy-dom
 */
describe('formDrafts', () => {
    beforeEach(() => localStorage.clear())

    it('keeps and forgets the answers of one form', () => {
        localStorage.setItem('storage_consent', 'accepted')

        saveFormDraft('station/form', {answers: {1: {text: 'halb'}}, path: ['p0', 'p1']})

        expect(readFormDraft('station/form')).toMatchObject({answers: {1: {text: 'halb'}}, path: ['p0', 'p1']})
        expect(readFormDraft('another')).toBeNull()
        clearFormDraft('station/form')
        expect(readFormDraft('station/form')).toBeNull()
    })

    it('keeps nothing where storage was not allowed', () => {
        saveFormDraft('station/form', {answers: {}, path: []})

        expect(readFormDraft('station/form')).toBeNull()
    })
})
