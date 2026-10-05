/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it} from 'vitest'
import {
    StorageNecessity, acceptStorage, denyStorage, getGrantedScopes, getItem, isStorageAllowed,
    setGrantedScopes, setItem,
} from './storage'

describe('storage consent', () => {
    beforeEach(() => localStorage.clear())

    it('writes no optional value before consent is given', () => {
        setItem('sidebar_collapsed', 'true')
        expect(getItem('sidebar_collapsed')).toBeNull()
    })

    /** A member who was never asked must still be able to stay in their station. */
    it('keeps required values before anybody was asked', () => {
        setItem('station_id', 'abc')
        expect(getItem('station_id')).toBe('abc')
    })

    it('allows required values when only those are accepted', () => {
        acceptStorage(undefined, [])
        setItem('station_id', 'abc')
        expect(getItem('station_id')).toBe('abc')
    })

    it('no longer keeps a session of any kind', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
        setItem('session_token', 'abc')
        expect(getItem('session_token')).toBeNull()
    })

    it('refuses an optional value while its group is not allowed', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL])
        setItem('sidebar_collapsed', 'true')
        expect(getItem('sidebar_collapsed')).toBeNull()
        expect(isStorageAllowed('instance_theme')).toBe(true)
    })

    it('removes what a withdrawn group had stored', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
        setItem('sidebar_collapsed', 'true')
        setItem('instance_theme', 'ember')

        setGrantedScopes([StorageNecessity.FUNCTIONAL])

        expect(getItem('sidebar_collapsed')).toBeNull()
        expect(getItem('instance_theme')).toBe('ember')
    })

    it('no longer keeps an AI key', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
        setItem('ai_api_key', 'sk-secret')
        expect(getItem('ai_api_key')).toBeNull()
    })

    it('treats a consent from before the groups as covering all of them', () => {
        localStorage.setItem('storage_consent', 'accepted')
        expect(getGrantedScopes()).toEqual([StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
    })

    it('keeps nothing optional after a denial', () => {
        acceptStorage(undefined, [StorageNecessity.COMFORT])
        setItem('sidebar_collapsed', 'true')

        denyStorage()

        expect(getGrantedScopes()).toEqual([])
        expect(getItem('sidebar_collapsed')).toBeNull()
        expect(isStorageAllowed('sidebar_collapsed')).toBe(false)
    })

    it('never writes a key that is not declared', () => {
        acceptStorage(undefined, [StorageNecessity.FUNCTIONAL, StorageNecessity.COMFORT])
        setItem('something_undeclared', 'x')
        expect(getItem('something_undeclared')).toBeNull()
    })
})
