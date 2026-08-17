/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it} from 'vitest'
import {
    StorageNecessity, acceptStorage, denyStorage, getGrantedScopes, getItem, isStorageAllowed,
    setGrantedScopes, setItem,
} from './storage'

describe('storage consent', () => {
    beforeEach(() => localStorage.clear())

    it('writes nothing before consent is given', () => {
        setItem('session_token', 'abc')
        expect(getItem('session_token')).toBeNull()
    })

    it('allows required values as soon as storage is accepted', () => {
        acceptStorage(undefined, [])
        setItem('session_token', 'abc')
        expect(getItem('session_token')).toBe('abc')
    })

    it('refuses an optional value while its group is not allowed', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL])
        setItem('sidebar_collapsed', 'true')
        expect(getItem('sidebar_collapsed')).toBeNull()
        expect(isStorageAllowed('ai_model')).toBe(true)
    })

    it('removes what a withdrawn group had stored', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
        setItem('sidebar_collapsed', 'true')
        setItem('ai_model', 'sonnet')

        setGrantedScopes([StorageNecessity.FUNCTIONAL])

        expect(getItem('sidebar_collapsed')).toBeNull()
        expect(getItem('ai_model')).toBe('sonnet')
    })

    it('treats a consent from before the groups as covering all of them', () => {
        localStorage.setItem('storage_consent', 'accepted')
        expect(getGrantedScopes()).toEqual([StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
    })

    it('keeps nothing after a denial', () => {
        acceptStorage(undefined, [StorageNecessity.COMFORT])
        setItem('sidebar_collapsed', 'true')

        denyStorage()

        expect(getGrantedScopes()).toEqual([])
        expect(getItem('sidebar_collapsed')).toBeNull()
        expect(isStorageAllowed('session_token')).toBe(false)
    })

    it('never writes a key that is not declared', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
        setItem('something_undeclared', 'x')
        expect(getItem('something_undeclared')).toBeNull()
    })
})
