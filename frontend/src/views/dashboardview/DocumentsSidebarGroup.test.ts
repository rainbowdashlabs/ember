/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import DocumentsSidebarGroup from './DocumentsSidebarGroup.vue'
import {StationPermission} from '@/api/generated/schema'
import {sessionWriter} from '@/util/sessionState'
import {createSessionInfo} from '@/test/mocks/factories'

/**
 * Every member has documents of their own, so the group is there for everybody; what lies beyond them
 * shows only to whoever holds the right for it.
 */
describe('DocumentsSidebarGroup', () => {
    function signIn(permissions: string[]) {
        sessionWriter().setInfo(createSessionInfo({
            permissions,
            member: {id: 1, stationId: 'test-station', accountId: 1, uid: 'member-uid-1', calledName: 'Member', nickname: null},
        }))
    }

    async function entries() {
        const group = await mountSuspended(DocumentsSidebarGroup, {props: {openGroup: '/station/documents', isDesktop: false}})
        return group.findAll('a').map(link => link.attributes('href'))
    }

    afterEach(() => {
        sessionWriter().setInfo(null)
    })

    it('leads a member without rights to their own documents only', async () => {
        signIn([])

        expect(await entries()).toEqual(['/station/documents'])
    })

    it('shows the store to whoever reads it, and the generated documents to whoever reads those of members', async () => {
        signIn([StationPermission.DOCUMENT_READ, StationPermission.DOCUMENT_READ_MEMBER])

        expect(await entries()).toEqual(['/station/documents', '/station/documents/store', '/station/documents/generated'])
    })

    it('shows templates and fonts to whoever writes templates', async () => {
        signIn([StationPermission.DOCUMENT_TEMPLATE_EDIT])

        expect(await entries()).toEqual(['/station/documents', '/station/documents/templates', '/station/documents/fonts'])
    })
})
