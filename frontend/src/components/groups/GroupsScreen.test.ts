/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import GroupsScreen from './GroupsScreen.vue'
import deDE from '@/i18n/de-DE'
import type {GroupsCapabilities, GroupsPort} from '@/composables/useGroupsConfig'

function fakePort(): GroupsPort {
  return {
    listGroups: vi.fn().mockResolvedValue([{id: 1, name: 'Vorstand'}]),
    listCandidates: vi.fn().mockResolvedValue([{id: 7, name: 'Erika Muster'}]),
    listAllRoles: vi.fn().mockResolvedValue([]),
    getDetail: vi.fn().mockResolvedValue({members: [{id: 7, name: 'Erika Muster'}], roles: []}),
    createGroup: vi.fn(),
    updateGroup: vi.fn(),
    deleteGroup: vi.fn(),
    setMembers: vi.fn(),
  }
}

async function screen(canEdit: boolean) {
  const capabilities: GroupsCapabilities = {
    hasColour: false,
    canConvertToTag: false,
    hasPermissions: false,
    permissionScope: 'cluster',
    holds: 'members',
    canEdit,
  }
  const view = mount(GroupsScreen, {
    props: {title: 'Gruppen', subtitle: 'Wer den Verband führt', selectHint: 'Wähle eine Gruppe', port: fakePort(),
      capabilities, canEditRoles: canEdit},
    global: {
      plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})],
      stubs: {
        ViewContent: {template: '<div><slot/></div>'},
        GroupFormModal: true,
        ConfirmDeleteModal: true,
        MemberName: {props: ['identity'], template: '<span>{{ identity.name }}</span>'},
      },
    },
  })
  await flushPromises()
  await view.find('.cursor-pointer').trigger('click')
  await flushPromises()
  return view
}

/** One screen for the association's groups, offering a write only to whoever the server lets write. */
describe('GroupsScreen', () => {
  it('lists and opens the groups without any control a reader may not use', async () => {
    const view = await screen(false)

    expect(view.text()).toContain('Vorstand')
    expect(view.text()).toContain('Erika Muster')
    expect(view.findAll('button')).toHaveLength(0)
  })

  it('offers creating, changing and removing to whoever may write', async () => {
    const view = await screen(true)

    expect(view.findAll('button').length).toBeGreaterThanOrEqual(4)
  })
})
