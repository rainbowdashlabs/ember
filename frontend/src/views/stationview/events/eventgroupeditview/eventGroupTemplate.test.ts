/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {AxiosError, type AxiosResponse} from 'axios'
import {groupFieldsOf, newByName, useEventGroupTemplate} from './eventGroupTemplate'
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
      field('Fahrer', {fieldType: 'MEMBER', config: {perDate: true, required: false, managersOnly: false, selfRegistration: false, calendarLocation: false}}),
      field('Thema', {attendanceFieldId: 3}),
    ], false)

    expect(taken.kept.map(entry => entry.name)).toEqual(['Ort', 'Betreuer'])
    expect(taken.kept[0]).toMatchObject({value: 'x', attendanceFieldId: null})
    expect(taken.leftOut).toEqual(['Fahrer', 'Thema'])
  })

  it('keeps a field tied to the sheet with its tie where the event group takes the sheet along', () => {
    const taken = groupFieldsOf([field('Thema', {attendanceFieldId: 3})], true)

    expect(taken.kept).toHaveLength(1)
    expect(taken.kept[0]).toMatchObject({name: 'Thema', attendanceFieldId: 3})
    expect(taken.leftOut).toEqual([])
  })
})

describe('useEventGroupTemplate', () => {
  beforeEach(() => vi.clearAllMocks())

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

  it('leaves out a file the library no longer holds and names it, attaching the rest', async () => {
    api.events.getTemplate.mockResolvedValue({template: {id: 5}})
    api.events.listTemplateAttachments.mockResolvedValue([
      {fileId: 40, label: 'Anfahrt', internal: false, fileName: 'anfahrt.pdf'},
      {fileId: 41, label: null, internal: true, fileName: 'plan.pdf'},
    ])
    api.eventGroups.attachFile.mockRejectedValueOnce(
        new AxiosError('gone', '404', undefined, undefined, {status: 404} as AxiosResponse))
    const template = useEventGroupTemplate()
    await template.read(5)

    expect(await template.attachFiles(7)).toEqual(['Anfahrt'])
    expect(api.eventGroups.attachFile).toHaveBeenLastCalledWith(7, 41, null, true)
    expect(template.files.value).toEqual([])
  })
})

/** A template adds to the form what it does not hold yet, matched by name as the server matches. */
describe('newByName', () => {
  it('leaves out names the form holds already, ignoring case and spaces, and names them', () => {
    const merged = newByName([{name: 'T-Shirt-Größe'}], [{name: ' t-shirt-größe '}, {name: 'Allergien'}, {name: 'allergien'}])

    expect(merged.added).toEqual([{name: 'Allergien'}])
    expect(merged.present).toEqual([' t-shirt-größe ', 'allergien'])
  })
})
