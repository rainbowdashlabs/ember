/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import FailedMailsSection from './FailedMailsSection.vue'
import type {FailedMail, FailedMailPage, StationFailedMails} from '@/api/generated/schema'

const getFailedInstanceMails = vi.hoisted(() => vi.fn())
const getFailedStationMails = vi.hoisted(() => vi.fn())
const acknowledgeFailedMail = vi.hoisted(() => vi.fn())
const acknowledgeAllFailedMails = vi.hoisted(() => vi.fn())
const acknowledgeStationFailedMails = vi.hoisted(() => vi.fn())
const requeueFailedMail = vi.hoisted(() => vi.fn())

vi.mock('@/api/failedMails', () => ({
    getFailedInstanceMails,
    getFailedStationMails,
    acknowledgeFailedMail,
    acknowledgeAllFailedMails,
    acknowledgeStationFailedMails,
    requeueFailedMail,
}))

function mail(overrides: Partial<FailedMail> = {}): FailedMail {
    return {
        id: 1, recipient: 'anna@test.de', subject: 'Willkommen', createdAt: '2026-11-10T08:00:00Z',
        failedAt: '2026-11-10T08:05:00Z', cause: 'RETRIES_EXHAUSTED', detail: null, requeueable: true, ...overrides,
    }
}

function page(mails: FailedMail[], total = mails.length): FailedMailPage {
    return {mails, total, page: 0, size: 25}
}

const NORD: StationFailedMails = {
    stationUid: '00000000-0000-0000-0000-000000000004', stationName: 'Wache Nord', count: 3,
    oldest: '2026-11-09T08:00:00Z', newest: '2026-11-10T09:00:00Z',
    causes: [{cause: 'NO_PROVIDER', count: 2}, {cause: 'SPAM', count: 1}],
}

async function open(onChanged?: () => void) {
    const view = await mountSuspended(FailedMailsSection, {props: {onChanged}})
    await flushPromises()
    return view
}

/**
 * The failed mails in the mail log: the instance's own in full with their actions, the stations'
 * counted without a recipient, and nothing at all while there is nothing to show.
 */
describe('FailedMailsSection', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        acknowledgeFailedMail.mockResolvedValue(undefined)
        acknowledgeAllFailedMails.mockResolvedValue({acknowledged: 2})
        acknowledgeStationFailedMails.mockResolvedValue({acknowledged: 3})
        requeueFailedMail.mockResolvedValue(undefined)
    })

    it('stays out of sight while nothing failed', async () => {
        getFailedInstanceMails.mockResolvedValue(page([]))
        getFailedStationMails.mockResolvedValue([])
        const view = await open()

        expect(view.find('[data-testid="failed-mails-section"]').exists()).toBe(false)
        expect(view.find('#failed').exists()).toBe(true)
        view.unmount()
    })

    it('lists the instance mails in full and the stations without recipients', async () => {
        getFailedInstanceMails.mockResolvedValue(page([
            mail(),
            mail({id: 2, recipient: 'ben@test.de', cause: 'HARD_BOUNCE', detail: '550 unknown user', requeueable: false}),
        ]))
        getFailedStationMails.mockResolvedValue([NORD])
        const view = await open()

        const instance = view.get('[data-testid="failed-instance-mails"]')
        expect(instance.text()).toContain('anna@test.de')
        expect(instance.text()).toContain('Alle Versuche aufgebraucht')
        expect(instance.text()).toContain('550 unknown user')
        expect(view.findAll('[data-testid="failed-requeue"]')).toHaveLength(1)
        const stations = view.get('[data-testid="failed-station-mails"]')
        expect(stations.text()).toContain('Wache Nord')
        expect(stations.text()).toContain('Kein Anbieter eingerichtet (2), Als Spam markiert (1)')
        view.unmount()
    })

    it('takes note, sends again and tells whoever counts, then reads the lists again', async () => {
        getFailedInstanceMails.mockResolvedValue(page([mail({id: 7})]))
        getFailedStationMails.mockResolvedValue([NORD])
        const onChanged = vi.fn()
        const view = await open(onChanged)

        await view.get('[data-testid="failed-acknowledge"]').trigger('click')
        await flushPromises()
        await view.get('[data-testid="failed-requeue"]').trigger('click')
        await flushPromises()
        await view.get('[data-testid="failed-station-acknowledge"]').trigger('click')
        await flushPromises()
        await view.get('[data-testid="failed-acknowledge-all"]').trigger('click')
        await flushPromises()

        expect(acknowledgeFailedMail).toHaveBeenCalledWith(7)
        expect(requeueFailedMail).toHaveBeenCalledWith(7)
        expect(acknowledgeStationFailedMails).toHaveBeenCalledWith(NORD)
        expect(acknowledgeAllFailedMails).toHaveBeenCalledWith('2026-11-10T08:05:00Z')
        expect(onChanged).toHaveBeenCalledTimes(4)
        expect(getFailedInstanceMails).toHaveBeenCalledTimes(5)
        view.unmount()
    })

    it('loads the next page below the first', async () => {
        getFailedInstanceMails
            .mockResolvedValueOnce(page([mail({id: 1})], 2))
            .mockResolvedValueOnce(page([mail({id: 2, recipient: 'zweite@test.de'})], 2))
        getFailedStationMails.mockResolvedValue([])
        const view = await open()

        await view.get('[data-testid="failed-more"]').trigger('click')
        await flushPromises()

        expect(getFailedInstanceMails).toHaveBeenLastCalledWith(1, 25)
        expect(view.text()).toContain('zweite@test.de')
        view.unmount()
    })
})
