/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {FieldType, type AppointmentField} from '@/api/generated/schema'
import EventMetaGrid from './EventMetaGrid.vue'

const eventPlaces = vi.fn()

vi.mock('@/api', () => ({
    events: {toggleFieldSelfRegistration: vi.fn()},
    places: {eventPlaces: (...args: unknown[]) => eventPlaces(...args)},
}))

function field(over: Partial<AppointmentField>): AppointmentField {
    return {
        id: 1,
        eventId: 7,
        name: 'Ort',
        fieldType: FieldType.LOCATION,
        value: 'Gerätehaus',
        config: {perDate: false, selfRegistration: false, required: false, managersOnly: false},
        position: 0,
        overview: true,
        isPublic: true,
        attendanceFieldId: null,
        selfRegistrationState: null,
        readerInWholeGroup: false,
        ...over,
    }
}

async function grid(fields: AppointmentField[], effectiveDate: string | null = null) {
    const mounted = await mountSuspended(EventMetaGrid, {
        props: {eventId: 7, fields, allMembers: [], currentMemberId: 1, templateName: '', canEditEvent: false, effectiveDate},
        global: {stubs: {PlaceMap: {props: ['latitude', 'longitude', 'label'], template: '<div data-testid="place-map">{{ label }} {{ latitude }}</div>'}}},
    })
    await flushPromises()
    return mounted
}

/**
 * A place field with a pin has a map button behind its text that opens a small map under it, closed
 * until it is pressed; every other field has neither.
 */
describe('EventMetaGrid places', () => {
    beforeEach(() => {
        eventPlaces.mockReset()
        eventPlaces.mockResolvedValue([
            {text: 'GERÄTEHAUS ', latitude: 52.1, longitude: 10.6, precision: 'EXACT', updatedAt: '2026-11-01T10:00:00Z'},
        ])
    })

    it('keeps the map closed until its button is pressed', async () => {
        const mounted = await grid([field({})])

        const toggle = mounted.find('[data-testid="event-meta-place-toggle"]')
        expect(toggle.attributes('aria-expanded')).toBe('false')
        expect(mounted.find('[data-testid="place-map"]').exists()).toBe(false)

        await toggle.trigger('click')
        expect(toggle.attributes('aria-expanded')).toBe('true')
        expect(mounted.find('[data-testid="place-map"]').exists()).toBe(true)

        await toggle.trigger('click')
        expect(mounted.find('[data-testid="place-map"]').exists()).toBe(false)
    })

    it('opens the map of one place field and leaves the others closed', async () => {
        eventPlaces.mockResolvedValue([
            {text: 'Gerätehaus', latitude: 52.1, longitude: 10.6, precision: 'EXACT', updatedAt: '2026-11-01T10:00:00Z'},
            {text: 'Sportplatz', latitude: 52.2, longitude: 10.7, precision: 'EXACT', updatedAt: '2026-11-01T10:00:00Z'},
        ])
        const mounted = await grid([field({}), field({id: 2, name: 'Treffpunkt', value: 'Sportplatz'})])

        const toggles = mounted.findAll('[data-testid="event-meta-place-toggle"]')
        expect(toggles).toHaveLength(2)
        await toggles[1]!.trigger('click')

        const maps = mounted.findAll('[data-testid="place-map"]')
        expect(maps).toHaveLength(1)
        expect(maps[0]!.text()).toContain('Sportplatz 52.2')
        expect(toggles[0]!.attributes('aria-expanded')).toBe('false')
        expect(toggles[1]!.attributes('aria-controls')).toBe('event-meta-place-map-2')
    })

    it('draws the pin of a place field under its text, whatever its spelling', async () => {
        const mounted = await grid([field({}), field({id: 2, name: 'Treffpunkt', value: 'Sportplatz'})], '2026-12-01')

        const toggles = mounted.findAll('[data-testid="event-meta-place-toggle"]')
        expect(toggles).toHaveLength(1)
        await toggles[0]!.trigger('click')
        const maps = mounted.findAll('[data-testid="place-map"]')
        expect(maps).toHaveLength(1)
        expect(maps[0]!.text()).toContain('Gerätehaus 52.1')
        expect(eventPlaces).toHaveBeenCalledWith(7, '2026-12-01')
    })

    it('asks for nothing where no field is a place', async () => {
        const mounted = await grid([field({fieldType: FieldType.TEXT})])

        expect(mounted.find('[data-testid="event-meta-place-toggle"]').exists()).toBe(false)
        expect(eventPlaces).not.toHaveBeenCalled()
    })

    it('shows the text alone when the pins cannot be read', async () => {
        eventPlaces.mockRejectedValue(new Error('offline'))
        const mounted = await grid([field({})])

        expect(mounted.text()).toContain('Gerätehaus')
        expect(mounted.find('[data-testid="event-meta-place-toggle"]').exists()).toBe(false)
    })
})
