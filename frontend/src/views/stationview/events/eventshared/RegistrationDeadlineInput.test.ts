/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount, type VueWrapper} from '@vue/test-utils'
import {defineComponent, h, ref} from 'vue'
import {describe, expect, it} from 'vitest'
import RegistrationDeadlineInput from './RegistrationDeadlineInput.vue'
import {noDeadline} from './registrationDeadline'
import type {RegistrationDeadline} from '@/api/generated/schema'

const global = {stubs: {'font-awesome-icon': true}}
const BERLIN = 'Europe/Berlin'

function relative(offsetMinutes: number, anchor: 'EXACT' | 'MIDNIGHT' = 'MIDNIGHT'): RegistrationDeadline {
    return {kind: 'RELATIVE', at: null, offsetMinutes, anchor}
}

function fixed(at: string | null): RegistrationDeadline {
    return {kind: 'FIXED', at, offsetMinutes: null, anchor: null}
}

function mountWith(modelValue: RegistrationDeadline, props: Record<string, unknown> = {}) {
    return mount(RegistrationDeadlineInput, {props: {modelValue, timeZone: BERLIN, ...props}, global})
}

function lastRule(wrapper: ReturnType<typeof mountWith>): RegistrationDeadline | undefined {
    return wrapper.emitted('update:modelValue')?.at(-1)?.[0] as RegistrationDeadline | undefined
}

function selected(wrapper: Pick<VueWrapper, 'get'>, testId: string): string {
    return (wrapper.get(`[data-testid="${testId}"]`).element as HTMLSelectElement).value
}

/**
 * One editor for every deadline: none, a fixed moment, or a span before each date's start. The span
 * is stored in minutes and shown in the unit it is whole in, and the next date's closing is
 * previewed on the station's clock.
 */
describe('RegistrationDeadlineInput', () => {
    it('shows no fields without a deadline', () => {
        const wrapper = mountWith(noDeadline())

        expect(selected(wrapper, 'registration-deadline-kind')).toBe('NONE')
        expect(wrapper.find('[data-testid="registration-deadline-at"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="registration-deadline-amount"]').exists()).toBe(false)
    })

    it('starts a span before the start at one day, at midnight', async () => {
        const wrapper = mountWith(noDeadline())

        await wrapper.get('[data-testid="registration-deadline-kind"]').setValue('RELATIVE')

        expect(lastRule(wrapper)).toEqual(relative(1440, 'MIDNIGHT'))
    })

    it('starts a fixed moment empty', async () => {
        const wrapper = mountWith(relative(2880))

        await wrapper.get('[data-testid="registration-deadline-kind"]').setValue('FIXED')

        expect(lastRule(wrapper)).toEqual(fixed(null))
    })

    it('comes back to the span set before after a detour', async () => {
        const wrapper = mountWith(relative(180, 'EXACT'))

        await wrapper.get('[data-testid="registration-deadline-kind"]').setValue('NONE')
        expect(lastRule(wrapper)).toEqual(noDeadline())
        await wrapper.setProps({modelValue: noDeadline()})
        await wrapper.get('[data-testid="registration-deadline-kind"]').setValue('RELATIVE')

        expect(lastRule(wrapper)).toEqual(relative(180, 'EXACT'))
    })

    it('shows whole days in days and anything else in hours', () => {
        const days = mountWith(relative(2880))
        expect(selected(days, 'registration-deadline-unit')).toBe('DAYS')
        expect((days.get('[data-testid="registration-deadline-amount"]').element as HTMLInputElement).value).toBe('2')

        const hours = mountWith(relative(180))
        expect(selected(hours, 'registration-deadline-unit')).toBe('HOURS')
        expect((hours.get('[data-testid="registration-deadline-amount"]').element as HTMLInputElement).value).toBe('3')
    })

    it('stores the number typed in minutes of the unit shown', async () => {
        const wrapper = mountWith(relative(1440))

        await wrapper.get('[data-testid="registration-deadline-amount"]').setValue('7')
        expect(lastRule(wrapper)?.offsetMinutes).toBe(10080)
    })

    it('keeps the number when the unit changes', async () => {
        const wrapper = mountWith(relative(2880))

        await wrapper.get('[data-testid="registration-deadline-unit"]').setValue('HOURS')
        expect(lastRule(wrapper)?.offsetMinutes).toBe(120)
        await wrapper.setProps({modelValue: relative(120)})

        await wrapper.get('[data-testid="registration-deadline-unit"]').setValue('DAYS')
        expect(lastRule(wrapper)?.offsetMinutes).toBe(2880)
    })

    it('stays in hours while a span typed in hours happens to be whole days', async () => {
        const wrapper = mountWith(relative(180))

        await wrapper.get('[data-testid="registration-deadline-amount"]').setValue('48')
        await wrapper.setProps({modelValue: lastRule(wrapper)})

        expect(selected(wrapper, 'registration-deadline-unit')).toBe('HOURS')
        expect((wrapper.get('[data-testid="registration-deadline-amount"]').element as HTMLInputElement).value).toBe('48')
    })

    /** A parent that hands the rule back as a fresh copy every time, as a computed model getter does. */
    it('keeps the unit chosen when the parent hands back a copy of every edit', async () => {
        const Parent = defineComponent(() => {
            const stored = ref<RegistrationDeadline>(relative(2880))
            return () => h(RegistrationDeadlineInput, {
                'modelValue': {...stored.value},
                'timeZone': BERLIN,
                'onUpdate:modelValue': (next: RegistrationDeadline) => { stored.value = {...next} },
            })
        })
        const wrapper = mount(Parent, {global})

        await wrapper.get('[data-testid="registration-deadline-unit"]').setValue('HOURS')
        expect(selected(wrapper, 'registration-deadline-unit')).toBe('HOURS')
        expect((wrapper.get('[data-testid="registration-deadline-amount"]').element as HTMLInputElement).value).toBe('2')

        await wrapper.get('[data-testid="registration-deadline-amount"]').setValue('48')
        expect(selected(wrapper, 'registration-deadline-unit')).toBe('HOURS')
        expect((wrapper.get('[data-testid="registration-deadline-amount"]').element as HTMLInputElement).value).toBe('48')
    })

    it('shows a rule that arrives from outside in the unit it is whole in', async () => {
        const wrapper = mountWith(noDeadline())

        await wrapper.setProps({modelValue: relative(10080)})

        expect(selected(wrapper, 'registration-deadline-unit')).toBe('DAYS')
        expect((wrapper.get('[data-testid="registration-deadline-amount"]').element as HTMLInputElement).value).toBe('7')

        await wrapper.setProps({modelValue: relative(180)})
        await wrapper.setProps({modelValue: relative(2880)})
        expect(selected(wrapper, 'registration-deadline-unit')).toBe('DAYS')
    })

    it('sets the anchor', async () => {
        const wrapper = mountWith(relative(1440, 'MIDNIGHT'))
        expect(selected(wrapper, 'registration-deadline-anchor')).toBe('MIDNIGHT')

        await wrapper.get('[data-testid="registration-deadline-anchor"]').setValue('EXACT')

        expect(lastRule(wrapper)).toEqual(relative(1440, 'EXACT'))
    })

    it('says when the next date closes, on the station clock', () => {
        const wrapper = mountWith(relative(7 * 1440, 'MIDNIGHT'), {nextStart: '2026-11-21T09:00:00Z'})

        const line = wrapper.get('[data-testid="registration-deadline-next"]').text()
        expect(line).toContain('Für den nächsten Termin')
        expect(line).toContain('14.11.2026')
        expect(line).toContain('00:00')
    })

    it('says nothing about the next date without one', () => {
        const wrapper = mountWith(relative(1440))

        expect(wrapper.find('[data-testid="registration-deadline-next"]').exists()).toBe(false)
    })

    it('warns that a fixed moment closes every date of a series at once', () => {
        expect(mountWith(fixed(null), {recurring: true}).find('[data-testid="registration-deadline-series-hint"]').exists()).toBe(true)
        expect(mountWith(fixed(null)).find('[data-testid="registration-deadline-series-hint"]').exists()).toBe(false)
        expect(mountWith(relative(1440), {recurring: true}).find('[data-testid="registration-deadline-series-hint"]').exists()).toBe(false)
    })

    it('stores a fixed moment as an instant', async () => {
        const wrapper = mountWith(fixed(null))

        const halves = wrapper.findAll('[data-testid="registration-deadline-at"] input')
        await halves.at(0)?.setValue('2026-11-14')
        await halves.at(1)?.setValue('18:30')

        expect(lastRule(wrapper)).toEqual(fixed(new Date('2026-11-14T18:30').toISOString()))
    })
})
