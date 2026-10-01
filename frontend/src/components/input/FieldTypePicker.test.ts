/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FieldTypePicker from './FieldTypePicker.vue'
import {FieldTypes, OfferedFieldTypes, type FieldTypeName} from '@/api/fieldTypes'

/**
 * The one select every feature picks a field's type in: it offers what the feature offers, calls each
 * type the same everywhere, and keeps an old field's type rather than swapping it for the first one.
 */
describe('FieldTypePicker', () => {
    function pick(type: FieldTypeName, types: readonly FieldTypeName[], unavailable: FieldTypeName[] = []) {
        return mount(FieldTypePicker, {props: {modelValue: type, types, unavailable}})
    }

    function offered(wrapper: ReturnType<typeof pick>) {
        return wrapper.findAll('option').map(option => option.attributes('value'))
    }

    it('offers exactly the types the feature offers, in its order', () => {
        expect(offered(pick(FieldTypes.TEXT, OfferedFieldTypes.WAITING_LIST))).toEqual([...OfferedFieldTypes.WAITING_LIST])
    })

    it('calls each type by its one name', () => {
        const labels = pick(FieldTypes.TEXT, [FieldTypes.TEXT, FieldTypes.CHOICE, FieldTypes.LONG_TEXT])
            .findAll('option').map(option => option.text())
        expect(labels).toEqual(['Text', 'Auswahl', 'Mehrzeiliger Text'])
    })

    it('keeps showing the type of a field saved under one no longer offered', () => {
        const values = offered(pick(FieldTypes.NUMBER, OfferedFieldTypes.ATTENDANCE))
        expect(values).toContain(FieldTypes.NUMBER)
        expect(values).toHaveLength(OfferedFieldTypes.ATTENDANCE.length + 1)
    })

    it('greys out a type that is offered but taken, unless it is the field own', () => {
        const wrapper = pick(FieldTypes.TEXT, OfferedFieldTypes.PROFILE, [FieldTypes.BIRTH_DATE])
        expect(wrapper.find('option[value="BIRTH_DATE"]').attributes('disabled')).toBeDefined()
        const own = pick(FieldTypes.BIRTH_DATE, OfferedFieldTypes.PROFILE, [FieldTypes.BIRTH_DATE])
        expect(own.find('option[value="BIRTH_DATE"]').attributes('disabled')).toBeUndefined()
    })

    it('hands back only a type it knows', async () => {
        const wrapper = pick(FieldTypes.TEXT, OfferedFieldTypes.BOARD)
        await wrapper.find('select').setValue(FieldTypes.LANE_ASSIGNEE)
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([FieldTypes.LANE_ASSIGNEE])
    })
})
