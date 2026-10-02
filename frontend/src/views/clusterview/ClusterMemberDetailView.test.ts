/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ClusterMemberDetailView from './ClusterMemberDetailView.vue'

vi.mock('@/api', () => ({
    clusterMembers: {
        getManagedMemberProfile: vi.fn(async () => ({
            memberId: 7,
            name: '',
            fields: [{
                id: 3,
                name: 'Funkrufname',
                fieldType: 'TEXT',
                config: {},
                required: false,
                position: 0,
                width: null,
                readonly: false,
                role: 'MEMBER',
                origin: 'CLUSTER',
                readonlyAtStation: false,
            }],
            values: [{fieldId: 3, value: '"Florian 4/83-1"', origin: 'CLUSTER'}],
        })),
        setManagedMemberProfile: vi.fn(),
    },
}))

vi.mock('@/api/clusterMembers', () => ({associationDocumentSource: {}}))

/**
 * The association's screen of one person at one of its stations.
 *
 * <p>The server names somebody only once they have left, so a current member arrives without a name.
 * The screen waited for one before it showed anything, and so stayed empty for everybody still there.
 */
describe('ClusterMemberDetailView', () => {
    it('shows the questions and the documents of a member the server sends without a name', async () => {
        const view = await mountSuspended(ClusterMemberDetailView, {
            global: {stubs: {MemberDocumentsPanel: true}},
        })
        await flushPromises()

        expect(view.text()).toContain('Funkrufname')
        expect(view.findAll('input').some(input => (input.element as HTMLInputElement).value === 'Florian 4/83-1'))
            .toBe(true)
        expect(view.find('[data-testid="cluster-member-documents"]').exists()).toBe(true)
    })
})
