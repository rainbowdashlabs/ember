/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {nextTick} from 'vue'
import {mount} from '@vue/test-utils'
import FieldTable from './FieldTable.vue'
import type {EditableField} from '@/api/profileFields'
import {FieldType, type FieldSet} from '@/api/generated/schema'

const ADDRESS: FieldSet = {id: 7, ownerId: 1, name: 'Adresse', nameAsSection: false}

function field(id: number, name: string, fieldSetId: number | null = null, setPosition = 0): EditableField {
    return {
        id, name, fieldType: FieldType.TEXT, config: {}, required: false, readonly: false, keepOnArchive: false,
        width: null, shownIfFieldId: null, fieldSetId, setPosition,
    }
}

const FIELDS = [field(1, 'Allein'), field(2, 'Straße', 7, 0), field(3, 'Ort', 7, 1)]

/** The questions of a group are ticked together, by one box on the group rather than one on each question. */
describe('FieldTable', () => {
    function table(checkedIds: number[]) {
        return mount(FieldTable, {
            props: {fields: FIELDS, sets: [ADDRESS], selectedId: null, audienceCount: {}, checkedIds: new Set(checkedIds)},
        })
    }

    function groupBox(view: ReturnType<typeof table>) {
        return view.get('[data-testid="field-set-check-Adresse"]').element as HTMLInputElement
    }

    it('gives a group one box and its questions none', () => {
        const view = table([])

        expect(view.find('[data-testid="field-check-Allein"]').exists()).toBe(true)
        expect(view.find('[data-testid="field-check-Straße"]').exists()).toBe(false)
        expect(view.find('[data-testid="field-check-Ort"]').exists()).toBe(false)
        expect(groupBox(view).checked).toBe(false)
    })

    it('ticks the group box when all of its questions are ticked, and marks it mixed for some', async () => {
        expect(groupBox(table([2, 3])).checked).toBe(true)

        const mixed = table([2])
        await nextTick()
        const some = groupBox(mixed)
        expect(some.checked).toBe(false)
        expect(some.indeterminate).toBe(true)
    })

    it('hands on the group when its box is pressed', async () => {
        const view = table([])

        await view.get('[data-testid="field-set-check-Adresse"]').trigger('click')

        expect(view.emitted('toggleSetChecked')).toEqual([[ADDRESS]])
    })
})
