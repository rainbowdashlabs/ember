/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ReminderResult, ReminderTrigger, StationState, type AdminStationSummary} from '@/api/generated/schema'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ManagerReminder from './ManagerReminder.vue'

const remindManager = vi.hoisted(() => vi.fn())
const getAdminStation = vi.hoisted(() => vi.fn())
const showToast = vi.hoisted(() => vi.fn())

vi.mock('@/api/stations', () => ({remindManager, getAdminStation}))
vi.mock('@/util/toast', () => ({showToast}))

const UID = '00000000-0000-0000-0000-000000000007'

function station(state: StationState, lastReminder: AdminStationSummary['lastReminder'] = null): AdminStationSummary {
    return {
        id: UID,
        name: 'Freiwillige Feuerwehr Nord',
        state,
        memberCount: 12,
        managerName: 'Mara Nager',
        managerEmail: 'mara@test.example',
        waitingSince: '2026-11-01T10:00:00Z',
        createdAt: '2026-11-01T10:00:00Z',
        associationName: null,
        lastReminder,
        movedAway: false,
    }
}

/**
 * The reminder to the manager of a station that waits, on the station list and the station's page.
 */
describe('ManagerReminder', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('reminds the manager of a waiting station and hands on the station as it stands afterwards', async () => {
        const reminded = station(StationState.WAITING_FOR_MANAGER, {sentAt: '2026-11-11T09:00:00Z', trigger: ReminderTrigger.MANUAL})
        remindManager.mockResolvedValue({station: reminded, result: ReminderResult.SENT})
        const onReminded = vi.fn()
        const view = mount(ManagerReminder, {props: {station: station(StationState.WAITING_FOR_MANAGER), onReminded}})

        expect(view.find('[data-testid="last-reminder"]').exists()).toBe(false)
        await view.find('[data-testid="remind-manager"]').trigger('click')
        await flushPromises()

        expect(remindManager).toHaveBeenCalledWith(UID)
        expect(onReminded).toHaveBeenCalledWith(reminded)
        expect(showToast).toHaveBeenCalledWith('Die Erinnerung an die Verwaltung ist unterwegs.', 'success')
    })

    it('says so where the address was counted as confirmed instead of written to', async () => {
        remindManager.mockResolvedValue({station: station(StationState.SETTING_UP), result: ReminderResult.CONFIRMED_WITHOUT_MAIL})
        const view = mount(ManagerReminder, {props: {station: station(StationState.WAITING_FOR_MANAGER)}})

        await view.find('[data-testid="remind-manager"]').trigger('click')
        await flushPromises()

        expect(showToast.mock.calls[0]![0]).toContain('als bestätigt markiert')
    })

    it('waits with the button for ten minutes after a reminder by hand', () => {
        const justNow = new Date(Date.now() - 60_000).toISOString()
        const view = mount(ManagerReminder, {
            props: {station: station(StationState.WAITING_FOR_MANAGER, {sentAt: justNow, trigger: ReminderTrigger.MANUAL})},
        })

        expect(view.find('[data-testid="remind-manager"]').attributes('disabled')).toBeDefined()
    })

    it('opens the button again once the ten minutes are over', async () => {
        vi.useFakeTimers()
        try {
            const justNow = new Date(Date.now() - 60_000).toISOString()
            const view = mount(ManagerReminder, {
                props: {station: station(StationState.WAITING_FOR_MANAGER, {sentAt: justNow, trigger: ReminderTrigger.MANUAL})},
            })
            expect(view.find('[data-testid="reminded-just-now"]').exists()).toBe(true)

            await vi.advanceTimersByTimeAsync(9 * 60_000 + 1_000)

            expect(view.find('[data-testid="remind-manager"]').attributes('disabled')).toBeUndefined()
            expect(view.find('[data-testid="reminded-just-now"]').exists()).toBe(false)
        } finally {
            vi.useRealTimers()
        }
    })

    it('keeps the refusal when reading the station again fails as well', async () => {
        const refusal = {isAxiosError: true, response: {status: 429, data: {code: 'S-084'}}}
        remindManager.mockRejectedValue(refusal)
        getAdminStation.mockRejectedValue(new Error('offline'))
        const onFailure = vi.fn()
        const view = mount(ManagerReminder, {props: {station: station(StationState.WAITING_FOR_MANAGER), compact: true, onFailure}})

        await view.find('[data-testid="remind-manager"]').trigger('click')
        await flushPromises()

        expect(onFailure).toHaveBeenLastCalledWith(expect.objectContaining({status: 429}))
    })

    it('reads the station afresh when the server says it was reminded only minutes ago', async () => {
        const fresh = station(StationState.WAITING_FOR_MANAGER, {sentAt: '2026-11-11T09:00:00Z', trigger: ReminderTrigger.MANUAL})
        remindManager.mockRejectedValue({isAxiosError: true, response: {status: 429, data: {code: 'S-084'}}})
        getAdminStation.mockResolvedValue(fresh)
        const onReminded = vi.fn()
        const view = mount(ManagerReminder, {props: {station: station(StationState.WAITING_FOR_MANAGER), onReminded}})

        await view.find('[data-testid="remind-manager"]').trigger('click')
        await flushPromises()

        expect(getAdminStation).toHaveBeenCalledWith(UID)
        expect(onReminded).toHaveBeenCalledWith(fresh)
    })

    it('draws the button alone in a table row and hands a refusal on', async () => {
        remindManager.mockRejectedValue(new Error('refused'))
        const onFailure = vi.fn()
        const view = mount(ManagerReminder, {
            props: {
                station: station(StationState.SETTING_UP, {sentAt: '2026-11-08T09:00:00Z', trigger: ReminderTrigger.AFTER_7_DAYS}),
                compact: true,
                onFailure,
            },
        })

        expect(view.find('[data-testid="last-reminder"]').exists()).toBe(false)
        await view.find('[data-testid="remind-manager"]').trigger('click')
        await flushPromises()

        expect(view.findComponent(FailureAlert).exists()).toBe(false)
        expect(onFailure).toHaveBeenLastCalledWith(expect.objectContaining({message: expect.any(String)}))
    })

    it('says when the last reminder went out and whether it went out by itself', () => {
        const byHand = mount(ManagerReminder, {
            props: {station: station(StationState.SETTING_UP, {sentAt: '2026-11-08T09:00:00Z', trigger: ReminderTrigger.MANUAL})},
        })
        const automatic = mount(ManagerReminder, {
            props: {station: station(StationState.SETTING_UP, {sentAt: '2026-11-08T09:00:00Z', trigger: ReminderTrigger.AFTER_7_DAYS})},
        })

        expect(byHand.find('[data-testid="last-reminder"]').text()).toBe('Zuletzt erinnert am 08.11.2026')
        expect(automatic.find('[data-testid="last-reminder"]').text()).toBe('Zuletzt automatisch erinnert am 08.11.2026')
        expect(byHand.find('[data-testid="remind-manager"]').exists()).toBe(true)
    })

    it('offers no reminder for a station that is set up or has no manager', () => {
        const setUp = mount(ManagerReminder, {props: {station: station(StationState.SET_UP)}})
        const noManager = mount(ManagerReminder, {props: {station: station(StationState.NO_MANAGER)}})

        expect(setUp.find('[data-testid="remind-manager"]').exists()).toBe(false)
        expect(noManager.find('[data-testid="remind-manager"]').exists()).toBe(false)
    })

    it('shows why a reminder was refused', async () => {
        remindManager.mockRejectedValue(new Error('refused'))
        const view = mount(ManagerReminder, {props: {station: station(StationState.WAITING_FOR_MANAGER)}})

        await view.find('[data-testid="remind-manager"]').trigger('click')
        await flushPromises()

        expect(view.findComponent(FailureAlert).props('failure')).not.toBeNull()
    })
})
