/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {reactive} from 'vue'
import type {EventGroupPartSettings} from '@/api/generated/schema'
import {useEventGroupPart} from './useEventGroupPart'

const api = vi.hoisted(() => ({
    eventGroups: {getEventGroup: vi.fn(), partSettings: vi.fn(), setPartLimit: vi.fn(), eventGroupOfEvent: vi.fn()},
}))
vi.mock('@/api', () => api)

function settings(ownRegistrationLimit: number | null): EventGroupPartSettings {
    return {
        eventGroup: {categoryId: null, templateId: null, reminderDays: [], fieldDefaults: []},
        eventGroupId: 70,
        ownCategoryId: null,
        ownTemplateId: null,
        ownRegistrationLimit,
        partRegistrationLimit: 12,
    }
}

async function partWith(own: number | null) {
    const form = reactive({templateId: '', categoryId: '', registrationLimit: 12 as number | undefined})
    api.eventGroups.getEventGroup.mockResolvedValue({fields: []})
    api.eventGroups.partSettings.mockResolvedValue(settings(own))
    const part = useEventGroupPart(() => 5, form)
    await part.load({
        eventGroupId: 70, eventGroupName: 'Jugendflamme', color: null, count: 2, number: 1, setId: 1, setName: 'Übung',
        firstStart: '2027-03-06T08:00:00Z', lastEnd: '2027-03-14T12:00:00Z',
    })
    part.keepOwnDateSettings()
    return {part, form}
}

/**
 * A part takes its event group's places per appointment until it sets its own. The form holds only
 * the part's own number, so saving never pins the inherited one.
 */
describe('useEventGroupPart places', () => {
    beforeEach(() => {
        api.eventGroups.setPartLimit.mockReset()
        api.eventGroups.setPartLimit.mockResolvedValue(settings(null))
    })

    it('puts the part\'s own places into the form, empty where it takes the event group\'s', async () => {
        expect((await partWith(null)).form.registrationLimit).toBeUndefined()
        expect((await partWith(20)).form.registrationLimit).toBe(20)
    })

    it('saves the places only where they changed', async () => {
        const {part} = await partWith(null)

        await part.saveOwnLimit(5, null)
        expect(api.eventGroups.setPartLimit).not.toHaveBeenCalled()

        await part.saveOwnLimit(5, 20)
        expect(api.eventGroups.setPartLimit).toHaveBeenCalledWith(5, 20)
    })
})
