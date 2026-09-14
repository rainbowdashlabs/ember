/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import AssignView from './AssignView.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'

const ON_THE_LIST = {
    id: 7,
    name: 'Erika Muster',
    identity: {memberUid: '11111111-1111-4111-8111-111111111111', name: 'Erika Muster'},
}

const ENTERED_SINCE = {
    id: 12,
    name: 'Neu Zugang',
    identity: {memberUid: '22222222-2222-4222-8222-222222222222', name: 'Neu Zugang'},
}

const listMembers = vi.fn()
const getMemberByUid = vi.fn()

vi.mock('@/api', () => ({
    stationMembers: {
        listMembers: (...args: unknown[]) => listMembers(...args),
        getMemberByUid: (...args: unknown[]) => getMemberByUid(...args),
    },
    inventory: {assignItem: vi.fn()},
    movements: {planHandOut: vi.fn()},
}))

/** Hands the screen somebody the way the menu does, by their UUID. */
async function pick(wrapper: Awaited<ReturnType<typeof mountSuspended>>, uid: string) {
    const menu = wrapper.findComponent(MemberSelectInput)
    menu.vm.$emit('update:modelValue', uid)
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
}

function pickerIsDisabled(wrapper: Awaited<ReturnType<typeof mountSuspended>>): boolean {
    return wrapper.findComponent(ItemSearchPicker).props('disabled') === true
}

/**
 * The menu asks the server as it is typed in and the screen holds the list it loaded when it opened,
 * so the menu offers people the list does not hold: anybody entered since, which on the day somebody
 * is written down and handed their gear is exactly who this screen is for.
 */
describe('AssignView', () => {
    beforeEach(() => {
        listMembers.mockReset()
        getMemberByUid.mockReset()
        listMembers.mockResolvedValue([ON_THE_LIST])
    })

    it('takes somebody the loaded list already holds without asking again', async () => {
        const wrapper = await mountSuspended(AssignView)

        await pick(wrapper, ON_THE_LIST.identity.memberUid)

        expect(getMemberByUid).not.toHaveBeenCalled()
        expect(pickerIsDisabled(wrapper)).toBe(false)
    })

    it('asks about somebody entered since the page opened, and can then act on them', async () => {
        getMemberByUid.mockResolvedValue(ENTERED_SINCE)
        const wrapper = await mountSuspended(AssignView)

        await pick(wrapper, ENTERED_SINCE.identity.memberUid)

        expect(getMemberByUid).toHaveBeenCalledWith(ENTERED_SINCE.identity.memberUid)
        expect(pickerIsDisabled(wrapper), 'the gear can be scanned for them').toBe(false)
    })

    it('says so where nobody of that name is at the station', async () => {
        getMemberByUid.mockResolvedValue(null)
        const wrapper = await mountSuspended(AssignView)

        await pick(wrapper, '33333333-3333-4333-8333-333333333333')

        expect(pickerIsDisabled(wrapper)).toBe(true)
        expect(wrapper.text()).toContain('Mitglied konnte nicht zugeordnet werden')
    })
})
