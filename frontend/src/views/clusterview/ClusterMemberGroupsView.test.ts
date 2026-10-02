/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {flushPromises, mount} from '@vue/test-utils'
import ClusterMemberGroupsView from './ClusterMemberGroupsView.vue'

const administrator = ref(false)

vi.mock('vue-i18n', () => ({useI18n: () => ({t: (key: string) => key})}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasClusterPermission: () => administrator.value}),
}))

vi.mock('@/api', () => ({clusterMembers: {}, data: {}}))

vi.mock('@/api/clusterMembers', () => ({clusterMemberIdentity: () => null}))

vi.mock('@/api/clusters', () => ({ClusterPermission: {CLUSTER_ADMINISTRATOR: 'CLUSTER_ADMINISTRATOR'}}))

/** The association's member groups offer writes to its administrators, once the session says who that is. */
describe('ClusterMemberGroupsView', () => {
    it('offers the writes once the session arrives saying the reader administers the association', async () => {
        administrator.value = false
        const view = mount(ClusterMemberGroupsView, {
            global: {
                stubs: {
                    GroupsScreen: {
                        props: ['capabilities', 'canEditRoles'],
                        template: '<div :data-can-edit="capabilities.canEdit" :data-roles="canEditRoles"/>',
                    },
                },
            },
        })
        const screen = () => view.find('[data-can-edit]')
        expect(screen().attributes('data-can-edit')).toBe('false')

        administrator.value = true
        await flushPromises()

        expect(screen().attributes('data-can-edit')).toBe('true')
        expect(screen().attributes('data-roles')).toBe('true')
    })
})
