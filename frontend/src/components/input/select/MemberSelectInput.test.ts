/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import MemberSelectInput from './MemberSelectInput.vue'
import type {MemberOption} from './memberOption'

const PEOPLE: MemberOption[] = [
    {value: '3', name: 'Zoe Abel', email: 'zoe@example.org', userType: 'MEMBER'},
    {value: '1', name: 'Anna Zimmer', email: 'anna@example.org', userType: 'GUARDIAN'},
    {value: '2', name: 'Ben Müller', email: 'ben@example.org', userType: 'MEMBER'},
]

/**
 * The one member menu, mounted the way every screen mounts it.
 *
 * <p>The avatar is stubbed because it fetches an authenticated image, which is its own concern.
 */
function mountMenu(props: Record<string, unknown> = {}) {
    return mount(MemberSelectInput, {
        props: {members: PEOPLE, ...props},
        global: {stubs: {UserAvatar: true, StationBadge: true}},
    })
}

async function openMenu(props: Record<string, unknown> = {}) {
    const wrapper = mountMenu(props)
    await wrapper.get('[data-testid="member-select-trigger"]').trigger('click')
    return wrapper
}

function rowTexts(wrapper: ReturnType<typeof mountMenu>): string[] {
    return wrapper.findAll('[data-testid="member-select-option"]').map(row => row.text())
}

describe('MemberSelectInput', () => {
    it('always offers a search', async () => {
        const wrapper = await openMenu()
        expect(wrapper.find('[data-testid="member-select-search"]').exists()).toBe(true)
    })

    it('offers a search even for a household of two', async () => {
        const wrapper = await openMenu({members: PEOPLE.slice(0, 2)})
        expect(wrapper.find('[data-testid="member-select-search"]').exists()).toBe(true)
    })

    it('orders the rows by first name', async () => {
        const wrapper = await openMenu()
        expect(rowTexts(wrapper).map(text => text.split(' ')[0])).toEqual(['Anna', 'Ben', 'Zoe'])
    })

    it('narrows the rows to what was typed', async () => {
        const wrapper = await openMenu()
        await wrapper.get('[data-testid="member-select-search"] input').setValue('müller')
        expect(rowTexts(wrapper)).toHaveLength(1)
        expect(rowTexts(wrapper)[0]).toContain('Ben Müller')
    })

    it('searches the address as well as the name', async () => {
        const wrapper = await openMenu()
        await wrapper.get('[data-testid="member-select-search"] input').setValue('zoe@example')
        expect(rowTexts(wrapper)).toHaveLength(1)
    })

    it('says so when nobody matches', async () => {
        const wrapper = await openMenu()
        await wrapper.get('[data-testid="member-select-search"] input').setValue('niemand')
        expect(rowTexts(wrapper)).toHaveLength(0)
        expect(wrapper.text()).toContain('Niemand passt dazu')
    })

    it('takes the highlighted row on Enter', async () => {
        const wrapper = await openMenu()
        await wrapper.get('[data-testid="member-select-panel"]').trigger('keydown', {key: 'Enter'})
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['1'])
    })

    it('walks down with the arrow keys before taking', async () => {
        const wrapper = await openMenu()
        const panel = wrapper.get('[data-testid="member-select-panel"]')
        await panel.trigger('keydown', {key: 'ArrowDown'})
        await panel.trigger('keydown', {key: 'Enter'})
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2'])
    })

    it('walks up and wraps to the last row', async () => {
        const wrapper = await openMenu()
        const panel = wrapper.get('[data-testid="member-select-panel"]')
        await panel.trigger('keydown', {key: 'ArrowUp'})
        await panel.trigger('keydown', {key: 'Enter'})
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['3'])
    })

    it('jumps to the ends with Home and End', async () => {
        const wrapper = await openMenu()
        const panel = wrapper.get('[data-testid="member-select-panel"]')
        await panel.trigger('keydown', {key: 'End'})
        await panel.trigger('keydown', {key: 'Enter'})
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['3'])
    })

    it('resets the highlight to the first match as the reader types', async () => {
        const wrapper = await openMenu()
        const panel = wrapper.get('[data-testid="member-select-panel"]')
        await panel.trigger('keydown', {key: 'End'})
        await wrapper.get('[data-testid="member-select-search"] input').setValue('ben')
        await panel.trigger('keydown', {key: 'Enter'})
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2'])
    })

    it('closes on Escape without taking anybody', async () => {
        const wrapper = await openMenu()
        await wrapper.get('[data-testid="member-select-panel"]').trigger('keydown', {key: 'Escape'})
        expect(wrapper.find('[data-testid="member-select-panel"]').exists()).toBe(false)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    it('opens from the closed trigger on ArrowDown', async () => {
        const wrapper = mountMenu()
        await wrapper.get('[data-testid="member-select-trigger"]').trigger('keydown', {key: 'ArrowDown'})
        expect(wrapper.find('[data-testid="member-select-panel"]').exists()).toBe(true)
    })

    it('offers no empty row unless the call site asks for one', async () => {
        const wrapper = await openMenu()
        expect(wrapper.text()).not.toContain('Nicht zugewiesen')
    })

    it('offers the empty row where the choice may be emptied', async () => {
        const wrapper = await openMenu({clearable: true})
        expect(wrapper.text()).toContain('Nicht zugewiesen')
    })

    it('keeps the empty answer apart from the people, so a story can tell them apart', async () => {
        const wrapper = await openMenu({clearable: true})
        expect(wrapper.findAll('[data-testid="member-select-empty"]')).toHaveLength(1)
        expect(rowTexts(wrapper)).toHaveLength(PEOPLE.length)
    })

    it('names the empty answer where nobody is not what it means', async () => {
        const wrapper = await openMenu({clearable: true, emptyLabel: 'Für mich selbst'})
        expect(wrapper.text()).toContain('Für mich selbst')
        expect(wrapper.text()).not.toContain('Nicht zugewiesen')
    })

    it('offers the kind filter only once more than one kind is on offer', async () => {
        const single = await openMenu({userTypes: ['MEMBER']})
        expect(single.find('[data-testid="member-select-user-type"]').exists()).toBe(false)
        const both = await openMenu({userTypes: ['MEMBER', 'GUARDIAN']})
        expect(both.find('[data-testid="member-select-user-type"]').exists()).toBe(true)
    })

    it('opens on the kind the screen is really asking for', async () => {
        const wrapper = await openMenu({userTypes: ['MEMBER', 'GUARDIAN'], openingUserType: 'GUARDIAN'})
        expect(rowTexts(wrapper)).toHaveLength(1)
        expect(rowTexts(wrapper)[0]).toContain('Anna Zimmer')
    })

    it('adds rather than replaces when it takes several', async () => {
        const wrapper = await openMenu({multiple: true, selected: ['1']})
        await wrapper.findAll('[data-testid="member-select-option"]')[1]!.trigger('click')
        expect(wrapper.emitted('update:selected')?.at(-1)).toEqual([['1', '2']])
    })

    it('takes somebody back off the list when they are clicked again', async () => {
        const wrapper = await openMenu({multiple: true, selected: ['1', '2']})
        await wrapper.findAll('[data-testid="member-select-option"]')[0]!.trigger('click')
        expect(wrapper.emitted('update:selected')?.at(-1)).toEqual([['2']])
    })

    it('shows what is chosen as chips', () => {
        const wrapper = mountMenu({multiple: true, selected: ['1', '2']})
        expect(wrapper.findAll('[data-testid="member-select-chip"]')).toHaveLength(2)
    })

    it('folds the chips past five and unfolds them again', async () => {
        const many: MemberOption[] = Array.from({length: 8}, (_, i) => ({value: String(i), name: `Person ${i}`}))
        const wrapper = mountMenu({multiple: true, members: many, selected: many.map(m => m.value)})
        expect(wrapper.findAll('[data-testid="member-select-chip"]')).toHaveLength(5)
        await wrapper.get('[data-testid="member-select-chip-fold"]').trigger('click')
        expect(wrapper.findAll('[data-testid="member-select-chip"]')).toHaveLength(8)
    })

    it('asks the server where a call site searches rather than holds', async () => {
        const searchFn = vi.fn().mockResolvedValue(PEOPLE)
        const wrapper = await openMenu({members: [], searchFn})
        await new Promise(resolve => setTimeout(resolve, 0))
        expect(searchFn).toHaveBeenCalledWith('')
        expect(rowTexts(wrapper)).toHaveLength(3)
    })

    it('puts a name to a choice made before it was drawn', async () => {
        const resolveFn = vi.fn().mockResolvedValue(PEOPLE[1])
        const wrapper = mountMenu({members: [], searchFn: vi.fn().mockResolvedValue([]), resolveFn, modelValue: '1'})
        await new Promise(resolve => setTimeout(resolve, 0))
        expect(resolveFn).toHaveBeenCalledWith('1')
        expect(wrapper.get('[data-testid="member-select-trigger"]').text()).toContain('Anna Zimmer')
    })
})
