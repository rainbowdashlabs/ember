/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {useEventFieldDefaults} from './useEventFieldDefaults'
import {eventFieldDefaultsPort, templateFieldDefaultsPort} from './eventOwnerPorts'

const api = vi.hoisted(() => ({
    events: {
        getFieldDefaults: vi.fn(),
        setFieldDefaults: vi.fn(),
        getTemplateFieldDefaults: vi.fn(),
        setTemplateFieldDefaults: vi.fn(),
    },
}))
vi.mock('@/api', () => api)

/**
 * The starting values of the attendance fields, for an event and for an event template. Which
 * owner they belong to is the port's to say; the rows the server sends back carry an id in a field
 * named for an event, and nothing here reads it.
 */
describe('useEventFieldDefaults', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.events.getFieldDefaults.mockResolvedValue([{eventId: 12, fieldId: 3, source: 'VALUE', value: 'Halle'}])
        api.events.setFieldDefaults.mockResolvedValue([])
        api.events.getTemplateFieldDefaults.mockResolvedValue([
            {eventId: 31, fieldId: 3, source: 'EVENT_NAME', value: null},
            {eventId: 31, fieldId: 4, source: 'VALUE', value: 'Hof'},
        ])
        api.events.setTemplateFieldDefaults.mockResolvedValue([])
    })

    it('reads and writes an event through the event routes', async () => {
        const defaults = useEventFieldDefaults()

        await defaults.load(eventFieldDefaultsPort(12))
        defaults.setValue(3, 'Hof')
        await defaults.save(eventFieldDefaultsPort(12), true)

        expect(api.events.getFieldDefaults).toHaveBeenCalledWith(12)
        expect(api.events.setFieldDefaults).toHaveBeenCalledWith(12, [{fieldId: 3, source: 'VALUE', value: 'Hof'}])
        expect(api.events.setTemplateFieldDefaults).not.toHaveBeenCalled()
    })

    it('reads and writes a template through the template routes', async () => {
        const defaults = useEventFieldDefaults()

        await defaults.load(templateFieldDefaultsPort(31))
        await defaults.save(templateFieldDefaultsPort(31), true)

        expect(api.events.getTemplateFieldDefaults).toHaveBeenCalledWith(31)
        expect(api.events.setTemplateFieldDefaults).toHaveBeenCalledWith(31, [
            {fieldId: 3, source: 'EVENT_NAME', value: undefined},
            {fieldId: 4, source: 'VALUE', value: 'Hof'},
        ])
        expect(api.events.setFieldDefaults).not.toHaveBeenCalled()
    })

    it('drops the values of fields left behind with another attendance sheet', async () => {
        const defaults = useEventFieldDefaults()
        await defaults.load(templateFieldDefaultsPort(31))

        defaults.keepOnly([4])
        await defaults.save(templateFieldDefaultsPort(31), true)

        expect(api.events.setTemplateFieldDefaults).toHaveBeenCalledWith(31, [{fieldId: 4, source: 'VALUE', value: 'Hof'}])
    })

    it('takes a template\'s values only for fields that hold none yet', async () => {
        const defaults = useEventFieldDefaults()
        defaults.setSource(4, 'VALUE')
        defaults.setValue(4, 'Halle')

        await defaults.takeFrom(templateFieldDefaultsPort(31))

        expect(defaults.defaults.value.get(3)).toEqual({source: 'EVENT_NAME', value: ''})
        expect(defaults.defaults.value.get(4)).toEqual({source: 'VALUE', value: 'Halle'})
    })
})
