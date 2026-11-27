/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {
    StorageNecessity, acceptStorage, clearStoredData, denyStorage, getGrantedScopes, getItem, getTabItem,
    isStorageAllowed, removeTabItem, setGrantedScopes, setItem, setTabItem,
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

/** The values one tab keeps for itself, in that tab's session storage. */
describe('tab storage', () => {
    beforeEach(() => {
        localStorage.clear()
        sessionStorage.clear()
    })

    afterEach(() => vi.restoreAllMocks())

    it('keeps a declared tab value in the session storage only', () => {
        setTabItem('tab_station_id', 'abc')

        expect(getTabItem('tab_station_id')).toBe('abc')
        expect(sessionStorage.getItem('tab_station_id')).toBe('abc')
        expect(localStorage.getItem('tab_station_id')).toBeNull()
    })

    it('writes no undeclared key', () => {
        setTabItem('not_declared', 'abc')

        expect(getTabItem('not_declared')).toBeNull()
    })

    it('is emptied with the stored data', () => {
        setTabItem('tab_station_id', 'abc')
        setTabItem('tab_cluster_id', 'def')

        clearStoredData()

        expect(getTabItem('tab_station_id')).toBeNull()
        expect(getTabItem('tab_cluster_id')).toBeNull()
    })

    /** A browser that blocks site data throws on any access to its storage. */
    it('keeps nothing and fails nothing where the browser blocks site data', () => {
        vi.spyOn(window, 'sessionStorage', 'get').mockImplementation(() => {
            throw new DOMException('blocked', 'SecurityError')
        })

        expect(() => setTabItem('tab_station_id', 'abc')).not.toThrow()
        expect(getTabItem('tab_station_id')).toBeNull()
        expect(() => removeTabItem('tab_station_id')).not.toThrow()
    })
})
