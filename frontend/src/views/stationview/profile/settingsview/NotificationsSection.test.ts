/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import NotificationsSection from './NotificationsSection.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {SettingsResponse} from '@/api/generated/schema'

/**
 * The notification switches of a member's settings.
 *
 * <p>The page sends back every type it switched, and the server refuses a save naming a type it no
 * longer knows. A row for such a type once made every later save of the page fail, so a row is
 * offered only for a type the server reported.
 */
describe('NotificationsSection', () => {
    const settings: SettingsResponse = {
        darkMode: 'system',
        feel: 'default',
        theme: 'default',
        emailEnabled: true,
        mailConfigured: true,
        mailProviders: [{type: 'SMTP', name: 'Beispiel Mail GmbH', url: ''}],
        notifications: {
            NEW_NEWS: {app: true, email: false, feed: true},
            EXPIRY_REMINDER: {app: true, email: false, feed: true},
        },
    }

    it('offers a switch only for the types the server reported', async () => {
        const view = mount(NotificationsSection, {props: {settings}})

        const switched = new Set<string>()
        const toggles = view.findAllComponents(ToggleInput)
        for (const toggle of toggles.slice(1)) {
            toggle.vm.$emit('update:modelValue', false)
        }
        for (const [type] of view.emitted('toggleApp') ?? []) switched.add(String(type))
        for (const [type] of view.emitted('toggleFeed') ?? []) switched.add(String(type))

        expect([...switched].sort()).toEqual(['EXPIRY_REMINDER', 'NEW_NEWS'])
    })

    it('names every provider the mail may go through, the type where no name was given', () => {
        const view = mount(NotificationsSection, {
            props: {
                settings: {
                    ...settings,
                    mailProviders: [
                        {type: 'SMTP', name: 'Wache Mail', url: 'https://wache.example/privacy'},
                        {type: 'BREVO', name: '', url: ''},
                    ],
                },
            },
        })

        const items = view.findAll('ol li').map(item => item.text())
        expect(items).toHaveLength(2)
        expect(items[0]).toContain('Wache Mail')
        expect(items[1]).toContain('Brevo')
        expect(view.find('ol a').attributes('href')).toBe('https://wache.example/privacy')
        expect(view.text()).toContain('„Wache Mail", „Brevo"')
        expect(view.text()).not.toContain('Unbekannt')
    })
})
