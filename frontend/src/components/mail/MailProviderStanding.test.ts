/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import MailProviderStanding from './MailProviderStanding.vue'
import MailPoolOverview from './MailPoolOverview.vue'
import type {ProviderStanding} from '@/api/generated/schema'

function standing(overrides: Partial<ProviderStanding>): ProviderStanding {
    return {
        position: 0, provider: 'BREVO', senderAddress: 'post@instanz.test', attempts: 2, dailySendLimit: 300,
        sentToday: 120, waiting: 0, exhausted: false, viaInstance: false, pool: null, ...overrides,
    }
}

/**
 * How a provider of the instance stands today, with the share stations may use of it.
 */
describe('MailProviderStanding', () => {
    it('shows the stations share of an instance provider and who used it, on the instance log', () => {
        const view = mount(MailProviderStanding, {props: {standing: standing({
            pool: {limit: 150, sentToday: 40, stations: [{instancePosition: 0, stationUid: 'x', name: 'Nord', sentToday: 40}]},
        })}})

        expect(view.text()).toContain('120 von 300 heute versendet')
        expect(view.text()).toContain('Wachen heute: 40 von 150')
        expect(view.text()).toContain('Nord 40')
    })

    it('reads a station\'s use of an instance provider against the share, not the provider\'s limit', () => {
        const view = mount(MailProviderStanding, {props: {standing: standing({
            viaInstance: true, sentToday: 4, exhausted: true, pool: {limit: 150, sentToday: 150, stations: []},
        })}})

        expect(view.text()).toContain('Instanz')
        expect(view.text()).toContain('4 Mails dieser Wache heute')
        expect(view.text()).not.toContain('von 300')
        expect(view.text()).toContain('Tageslimit erreicht')
    })

    it('says that a provider without a daily limit has no share', () => {
        const view = mount(MailProviderStanding, {props: {standing: standing({
            dailySendLimit: 0, pool: {limit: null, sentToday: 9, stations: []},
        })}})

        expect(view.text()).toContain('ohne Anteil')
    })
})

describe('MailPoolOverview', () => {
    const providers = [standing({}), standing({position: 1, provider: 'SMTP', dailySendLimit: 0})]

    it('warns about providers without a daily limit while stations are granted', () => {
        const view = mount(MailPoolOverview, {props: {pool: {sharePercent: 50, grantedStations: 2}, providers}})

        expect(view.text()).toContain('50 %')
        expect(view.find('[data-testid="mail-pool-no-limit"]').text()).toContain('2. Eigener Server')
    })

    it('stays quiet about the limit while no station is granted', () => {
        const view = mount(MailPoolOverview, {props: {pool: {sharePercent: 50, grantedStations: 0}, providers}})

        expect(view.find('[data-testid="mail-pool-no-limit"]').exists()).toBe(false)
    })
})
