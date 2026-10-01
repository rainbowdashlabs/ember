/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventQuestionEditor from './EventQuestionEditor.vue'
import type {EventFieldEntry} from '@/api/generated/schema'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import {asSaved, blankQuestion, emptySettings, type EventQuestionMode} from './eventQuestions'

/**
 * One editor for both kinds of appointment question: each mode offers its own types and shows only
 * the settings it owns.
 */
describe('EventQuestionEditor', () => {
    async function edit(mode: EventQuestionMode, question: EventFieldEntry = blankQuestion()) {
        return mountSuspended(EventQuestionEditor, {props: {mode, modelValue: question, showValue: true}})
    }

    function offered(wrapper: Awaited<ReturnType<typeof edit>>) {
        return wrapper.findAll('[data-testid="event-field-type"] option').map(option => option.attributes('value'))
    }

    it('offers the organiser every appointment type and the registrant the registration ones', async () => {
        expect(offered(await edit('organiser'))).toEqual([...OfferedFieldTypes.APPOINTMENT])
        expect(offered(await edit('registrant'))).toEqual([...OfferedFieldTypes.REGISTRATION])
    })

    it('asks the registrant question whether it is required, and not the organiser question', async () => {
        expect((await edit('registrant')).find('[data-testid="question-required"]').exists()).toBe(true)
        expect((await edit('organiser')).find('[data-testid="question-required"]').exists()).toBe(false)
    })

    it('carries an answer only on the organiser question', async () => {
        const named = {...blankQuestion(), name: 'Ort'}
        expect((await edit('organiser', named)).find('[data-testid="event-field-value"]').exists()).toBe(true)
        expect((await edit('registrant', named)).find('[data-testid="event-field-value"]').exists()).toBe(false)
    })

    it('hands back the whole question when its name changes', async () => {
        const wrapper = await edit('registrant', {...blankQuestion(), overview: true})
        await wrapper.find('[data-testid="event-field-name"]').setValue('Shirtgröße')
        const handed = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as EventFieldEntry
        expect(handed.name).toBe('Shirtgröße')
        expect(handed.overview).toBe(true)
    })
})

describe('asSaved', () => {
    it('keeps only what the type reads', () => {
        const saved = asSaved({
            name: 'Ausbilder',
            fieldType: 'MEMBER_OF_GROUP',
            config: {...emptySettings(), options: ['A', ' '], groupId: 3, tagId: 4, selfRegistration: true, min: 1},
        })
        expect(saved.config).toMatchObject({groupId: 3, selfRegistration: true})
        expect(saved.config?.options).toBeUndefined()
        expect(saved.config?.tagId).toBeUndefined()
        expect(saved.config?.min).toBeUndefined()
    })

    it('drops the empty rows of a choice', () => {
        const saved = asSaved({name: 'Größe', fieldType: 'CHOICE', config: {...emptySettings(), options: ['S', '', ' M ']}})
        expect(saved.config?.options).toEqual(['S', 'M'])
    })
})
