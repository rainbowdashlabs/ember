/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it} from 'vitest'
import {getItem, getTabItem, setItem} from '@/api/storage'
import {
    chooseTabCluster, chooseTabStation, forgetTabCluster, forgetTabPlaces, forgetTabStation, tabClusterId,
    tabStationId,
} from './tabPlace'

/**
 * The station and cluster of a tab. The shared storage only says where a new tab starts; once a tab
 * has one of its own, a switch in another tab, which writes the shared storage, no longer reaches it.
 */
describe('the place of a tab', () => {
    beforeEach(() => {
        localStorage.clear()
        sessionStorage.clear()
    })

    it('starts a new tab at the station chosen last', () => {
        setItem('station_id', 'chosen-last')

        expect(tabStationId()).toBe('chosen-last')
        expect(getTabItem('tab_station_id')).toBe('chosen-last')
    })

    it('keeps the station of a tab when another tab moves elsewhere', () => {
        setItem('station_id', 'first')
        expect(tabStationId()).toBe('first')

        setItem('station_id', 'second')

        expect(tabStationId()).toBe('first')
    })

    it('moves the tab and where the next tab starts on a choice', () => {
        chooseTabStation('picked')

        expect(tabStationId()).toBe('picked')
        expect(getItem('station_id')).toBe('picked')
    })

    it('has no station where neither the tab nor the browser has one', () => {
        expect(tabStationId()).toBeNull()
    })

    it('leaves the tab without a station once it is forgotten', () => {
        chooseTabStation('picked')

        forgetTabStation()

        expect(tabStationId()).toBeNull()
        expect(getItem('station_id')).toBeNull()
    })

    it('leaves the choice of another tab alone when it forgets its own', () => {
        chooseTabStation('lost')
        setItem('station_id', 'chosen-in-another-tab')

        forgetTabStation()

        expect(tabStationId()).toBe('chosen-in-another-tab')
        expect(getItem('station_id')).toBe('chosen-in-another-tab')
    })

    it('forgets a cluster the tab lost without touching the choice of another tab', () => {
        chooseTabCluster('lost')
        setItem('cluster_id', 'chosen-in-another-tab')

        forgetTabCluster()

        expect(getItem('cluster_id')).toBe('chosen-in-another-tab')
    })

    it('forgets everything on sign-out, also what another tab chose', () => {
        chooseTabStation('mine')
        setItem('station_id', 'chosen-in-another-tab')

        forgetTabPlaces()

        expect(getTabItem('tab_station_id')).toBeNull()
        expect(getItem('station_id')).toBeNull()
    })

    it('keeps the cluster of a tab the same way', () => {
        chooseTabCluster('own')
        setItem('cluster_id', 'other')

        expect(tabClusterId()).toBe('own')
    })

    it('forgets station and cluster on sign-out', () => {
        chooseTabStation('station')
        chooseTabCluster('cluster')

        forgetTabPlaces()

        expect(tabStationId()).toBeNull()
        expect(tabClusterId()).toBeNull()
    })
})
