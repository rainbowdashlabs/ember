/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {createI18n} from 'vue-i18n'
import de from '@/i18n/de-DE'
import {EventType} from '@/api/generated/schema'
import {eventTypeLabelKey} from './eventTypeLabel'

const {t} = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}}).global

/**
 * Each kind of appointment is called by its own name, not by the name of the weekly series.
 *
 * @vitest-environment happy-dom
 */
describe('eventTypeLabelKey', () => {
    it.each([
        [EventType.ONE_TIME, 'Einmalig'],
        [EventType.RECURRING, 'Wöchentlich'],
        [EventType.MONTHLY_FIRST, 'Monatlich (1. Wochentag)'],
        [EventType.QUARTERLY, 'Vierteljährlich (1. Wochentag)'],
        [EventType.YEARLY, 'Jährlich'],
    ])('names %s as %s', (eventType, label) => {
        expect(t(eventTypeLabelKey(eventType))).toBe(label)
    })

    it('reads an appointment without a kind as a one-off', () => {
        expect(eventTypeLabelKey(undefined)).toBe('events.typeOneTime')
    })
})
