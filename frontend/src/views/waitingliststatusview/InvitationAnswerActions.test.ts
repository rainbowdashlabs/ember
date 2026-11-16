/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import type {WaitingListPublicAppointment, WaitingListPublicInvitation} from '@/api/generated/schema'
import InvitationAnswerActions from './InvitationAnswerActions.vue'
import InvitationDetails from './InvitationDetails.vue'

const global = {stubs: {'font-awesome-icon': true}}

const DRILL: WaitingListPublicAppointment = {
    position: 0,
    appointmentName: 'Dienstabend',
    appointmentDate: '24.11.2099',
    appointmentTime: '18:00 - 20:00',
    arrivalTime: '',
    location: '',
}

function actions(appointments: WaitingListPublicAppointment[], noDatesLeft: boolean) {
    return mount(InvitationAnswerActions, {
        props: {answering: false, appointments, noDatesLeft, note: '', picked: ''},
        global,
    })
}

function invitation(appointments: WaitingListPublicAppointment[], noDatesLeft: boolean): WaitingListPublicInvitation {
    return {round: 1, appointments, message: '', noDatesLeft}
}

/**
 * Once every offered date is over, the family cannot come to one: the page says the station sends new
 * dates, and only saying no is left to answer.
 */
describe('the answer page without dates left', () => {
    it('offers all three answers while a date is ahead', () => {
        const wrapper = actions([DRILL], false)

        expect(wrapper.find('[data-testid="waitlist-answer-coming"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="waitlist-answer-date"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="waitlist-answer-no"]').exists()).toBe(true)
    })

    it('offers only saying no once every date is over', () => {
        const wrapper = actions([], true)

        expect(wrapper.find('[data-testid="waitlist-answer-coming"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="waitlist-answer-date"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="waitlist-answer-no"]').exists()).toBe(true)
    })

    it('says that the station sends new dates', () => {
        const left = mount(InvitationDetails, {props: {invitation: invitation([], true)}, global})
        const ahead = mount(InvitationDetails, {props: {invitation: invitation([DRILL], false)}, global})

        expect(left.find('[data-testid="waitlist-invitation-no-dates"]').text()).toContain('neue Termine')
        expect(ahead.find('[data-testid="waitlist-invitation-no-dates"]').exists()).toBe(false)
    })
})
