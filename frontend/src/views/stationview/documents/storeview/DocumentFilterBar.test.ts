/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import DocumentFilterBar from './DocumentFilterBar.vue'

function bar(readsStationDocuments: boolean) {
    return shallowMount(DocumentFilterBar, {
        props: {
            search: '',
            members: [],
            unbound: false,
            departed: false,
            memberOptions: [],
            readsMembers: true,
            readsStationDocuments,
        },
        global: {renderStubDefaultSlot: true},
    })
}

/** The filter for the station's own paperwork is offered only to whoever may read it. */
describe('DocumentFilterBar', () => {
    it('offers the unbound filter to a reader of the station paperwork', () => {
        expect(bar(true).find('[data-testid="documents-unbound"]').exists()).toBe(true)
    })

    it('hides the unbound filter from a reader of member documents alone', () => {
        expect(bar(false).find('[data-testid="documents-unbound"]').exists()).toBe(false)
    })
})
