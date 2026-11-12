/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {BINDING_EXCLUDES_MEMBERS, type GroupConflict} from '@/util/groupRules'
import GroupConflictModal from './GroupConflictModal.vue'

async function modalFor(code: string) {
    const conflict: GroupConflict = {code, conflicts: [{memberId: 4, memberName: 'Lena Lang', groups: ['Jugend', 'Atemschutz']}]}
    return mountSuspended(GroupConflictModal, {
        props: {conflict},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, NuxtLink: {template: '<a><slot/></a>'}}},
    })
}

/**
 * A member left out by the group's types is named on their own, since the group is the one being
 * edited; a member in two groups of a set is named with those groups, which is what the manager decides between.
 */
describe('GroupConflictModal', () => {
    it('names members left out by the types without their groups', async () => {
        const modal = await modalFor(BINDING_EXCLUDES_MEMBERS)

        expect(modal.text()).toContain('Lena Lang')
        expect(modal.text()).not.toContain('Jugend')
    })

    it('names members in two groups of a set with those groups', async () => {
        const modal = await modalFor('M-127')

        expect(modal.text()).toContain('Lena Lang')
        expect(modal.text()).toContain('Jugend, Atemschutz')
    })
})
