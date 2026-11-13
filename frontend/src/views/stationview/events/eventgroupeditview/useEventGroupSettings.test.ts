/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {useEventGroupSettings} from './useEventGroupSettings'

const api = vi.hoisted(() => ({eventGroups: {getSettings: vi.fn(), setSettings: vi.fn()}}))
vi.mock('@/api', () => api)

describe('useEventGroupSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.eventGroups.getSettings.mockResolvedValue({
      templateId: 4, categoryId: 2, reminderDays: [3],
      fieldDefaults: [{eventId: 7, fieldId: 11, source: 'VALUE', value: 'Herr Krause'}],
    })
  })

  it('sends back what it read, pre-fills included', async () => {
    const settings = useEventGroupSettings()
    await settings.load(7)

    expect(settings.request()).toEqual({
      templateId: 4, categoryId: 2, reminderDays: [3],
      fieldDefaults: [{fieldId: 11, source: 'VALUE', value: 'Herr Krause'}],
    })
  })

  it('sends no pre-fills once the template is cleared', async () => {
    const settings = useEventGroupSettings()
    await settings.load(7)
    settings.setTemplate('')
    settings.setCategory('')
    settings.setReminders([])

    expect(settings.request()).toEqual({templateId: null, categoryId: null, reminderDays: [], fieldDefaults: []})
  })
})
