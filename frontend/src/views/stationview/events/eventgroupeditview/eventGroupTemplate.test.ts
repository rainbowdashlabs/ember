/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {groupFieldsOf, useEventGroupTemplate} from './eventGroupTemplate'
import type {AppointmentTemplateField} from '@/api/generated/schema'

const api = vi.hoisted(() => ({
  events: {getTemplate: vi.fn(), listTemplateAttachments: vi.fn(), listTemplates: vi.fn()},
  eventGroups: {attachFile: vi.fn()},
}))
vi.mock('@/api', () => api)

function field(name: string, overrides: Partial<AppointmentTemplateField> = {}): AppointmentTemplateField {
  return {
    id: 1, templateId: 5, position: 0, name, fieldType: 'TEXT', defaultValue: 'x', overview: false,
    attendanceFieldId: null, isPublic: true,
    config: {perDate: false, required: false, managersOnly: false, selfRegistration: false},
    ...overrides,
  } as AppointmentTemplateField
}

describe('groupFieldsOf', () => {
  it('takes plain and member fields and names the ones that belong to one date', () => {
    const taken = groupFieldsOf([
      field('Ort'),
      field('Betreuer', {fieldType: 'MEMBER_LIST'}),
      field('Fahrer', {fieldType: 'MEMBER', config: {perDate: true, required: false, managersOnly: false, selfRegistration: false}}),
      field('Thema', {attendanceFieldId: 3}),
    ])

    expect(taken.kept.map(entry => entry.name)).toEqual(['Ort', 'Betreuer'])
    expect(taken.kept[0]).toMatchObject({value: 'x', attendanceFieldId: null})
    expect(taken.leftOut).toEqual(['Fahrer', 'Thema'])
  })
})

describe('useEventGroupTemplate', () => {
  it('attaches the template files once, in their order', async () => {
    api.events.getTemplate.mockResolvedValue({template: {id: 5}})
    api.events.listTemplateAttachments.mockResolvedValue([
      {fileId: 40, label: 'Anfahrt', internal: false},
      {fileId: 41, label: null, internal: true},
    ])
    const template = useEventGroupTemplate()
    await template.read(5)

    await template.attachFiles(7)
    await template.attachFiles(7)

    expect(api.eventGroups.attachFile.mock.calls).toEqual([[7, 40, 'Anfahrt', false], [7, 41, null, true]])
  })
})
