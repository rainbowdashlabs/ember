/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import EventGroupMaterialsPanel from './EventGroupMaterialsPanel.vue'

const api = vi.hoisted(() => ({
  appointmentDocuments: {listRequirements: vi.fn()},
  eventGroups: {listAttachments: vi.fn(), attachmentUrl: (groupId: number, id: number) => `/events/groups/${groupId}/attachments/${id}/file`},
  events: {},
}))
vi.mock('@/api', () => api)

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

describe('EventGroupMaterialsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.eventGroups.listAttachments.mockResolvedValue([])
  })

  it('lists the documents the event group asks every part for', async () => {
    api.appointmentDocuments.listRequirements.mockResolvedValue([{templateId: 4, name: 'Einverständnis'}])
    const wrapper = mount(EventGroupMaterialsPanel, {props: {eventGroupId: 7}, global: {plugins: [i18n]}})
    await flushPromises()

    expect(api.appointmentDocuments.listRequirements).toHaveBeenCalledWith({kind: 'events/groups', id: 7})
    expect(api.eventGroups.listAttachments).toHaveBeenCalledWith(7)
    expect(wrapper.findAll('[data-testid="event-group-document"]').map(row => row.text())).toEqual(['Einverständnis'])
  })

  it('says nothing about documents where the event group asks for none', async () => {
    api.appointmentDocuments.listRequirements.mockResolvedValue([])
    const wrapper = mount(EventGroupMaterialsPanel, {props: {eventGroupId: 7}, global: {plugins: [i18n]}})
    await flushPromises()

    expect(wrapper.find('[data-testid="event-group-documents"]').exists()).toBe(false)
  })
})
