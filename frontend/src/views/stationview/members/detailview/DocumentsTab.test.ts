/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import {StationPermission} from '@/api/generated/schema'
import MemberDocumentsPanel from '@/components/documents/MemberDocumentsPanel.vue'
import DocumentsTab from './DocumentsTab.vue'

const held = new Set<string>()
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({
        hasPermission: (permission: string) => held.has(permission),
        isModuleEnabled: () => true,
    }),
}))

function panelOf(...permissions: string[]) {
    held.clear()
    for (const permission of permissions) held.add(permission)
    const wrapper = shallowMount(DocumentsTab, {props: {memberId: 2, allMembers: []}})
    return wrapper.findComponent(MemberDocumentsPanel)
}

/** What the documents tab offers follows the permission for member documents, not the one for member data. */
describe('DocumentsTab', () => {
    it('offers no upload or editing to whoever only edits member data', () => {
        const panel = panelOf(StationPermission.MEMBER_EDIT, StationPermission.DOCUMENT_READ_MEMBER)

        expect(panel.props('canUpload')).toBe(false)
        expect(panel.props('canEdit')).toBe(false)
        expect(panel.props('manageSignatures')).toBe(false)
    })

    it('offers upload, editing and signatures to whoever manages member documents', () => {
        const panel = panelOf(StationPermission.DOCUMENT_EDIT_MEMBER)

        expect(panel.props('canUpload')).toBe(true)
        expect(panel.props('canEdit')).toBe(true)
        expect(panel.props('manageSignatures')).toBe(true)
    })
})
