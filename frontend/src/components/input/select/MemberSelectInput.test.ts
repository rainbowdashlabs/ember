/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {DOMWrapper, enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import MemberSelectInput from './MemberSelectInput.vue'
import type {MemberOption} from './memberOption'

enableAutoUnmount(afterEach)

const PEOPLE: MemberOption[] = [
    {value: '3', name: 'Zoe Abel', email: 'zoe@example.org', userType: 'MEMBER'},
    {value: '1', name: 'Anna Zimmer', email: 'anna@example.org', userType: 'GUARDIAN'},
    {value: '2', name: 'Ben Müller', email: 'ben@example.org', userType: 'MEMBER'},
]

/**
 * The one member menu, mounted the way every screen mounts it.
 *
 * <p>The avatar is stubbed because it fetches an authenticated image, which is its own concern. The
 * panel opens at the end of the page, so what is in it is looked for there.
 */
function mountMenu(props: Record<string, unknown> = {}) {
    return mount(MemberSelectInput, {
        props: {members: PEOPLE, ...props},
        global: {stubs: {UserAvatar: true, StationBadge: true}},
        attachTo: document.body,
    })
}

function page() {
    return new DOMWrapper(document.body)
}

async function openMenu(props: Record<string, unknown> = {}) {
    const wrapper = mountMenu(props)
    await wrapper.get('[data-testid="member-select-trigger"]').trigger('click')
    await flushPromises()
    return wrapper
}

function panel() {
    return page().find('[data-testid="member-select-panel"]')
}

function searchBox() {
    return page().get('[data-testid="member-select-search"] input')
}

async function press(key: string) {
    await searchBox().trigger('keydown', {key})
    await flushPromises()
}

function rowTexts(): string[] {
    return page().findAll('[data-testid="member-select-option"]').map(row => row.text())
}

describe('MemberSelectInput', () => {
    it('always offers a search', async () => {
        await openMenu()
        expect(page().find('[data-testid="member-select-search"]').exists()).toBe(true)
    })

    it('offers a search even for a household of two', async () => {
        await openMenu({members: PEOPLE.slice(0, 2)})
        expect(page().find('[data-testid="member-select-search"]').exists()).toBe(true)
    })

    it('is a list of options a screen reader can walk', async () => {
        await openMenu()
        expect(panel().find('[role="listbox"]').exists()).toBe(true)
        expect(page().findAll('[role="option"]')).toHaveLength(PEOPLE.length)
    })

    it('orders the rows by first name', async () => {
        await openMenu()
        expect(rowTexts().map(text => text.split(' ')[0])).toEqual(['Anna', 'Ben', 'Zoe'])
    })

    it('narrows the rows to what was typed', async () => {
        await openMenu()
        await searchBox().setValue('müller')
        expect(rowTexts()).toHaveLength(1)
        expect(rowTexts()[0]).toContain('Ben Müller')
    })

    it('searches the address as well as the name', async () => {
        await openMenu()
        await searchBox().setValue('zoe@example')
        expect(rowTexts()).toHaveLength(1)
    })

    it('says so when nobody matches', async () => {
        await openMenu()
        await searchBox().setValue('niemand')
        expect(rowTexts()).toHaveLength(0)
        expect(panel().text()).toContain('Niemand passt dazu')
    })

    /** The search keeps the focus and points at the highlighted row, which is how the row is announced. */
    it('points the search at the highlighted row', async () => {
        await openMenu()
        const highlighted = page().get('[role="option"][data-highlighted]')
        expect(searchBox().attributes('aria-activedescendant')).toBe(highlighted.attributes('id'))
    })

    it('takes the highlighted row on Enter', async () => {
        const wrapper = await openMenu()
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['1'])
        expect(panel().exists()).toBe(false)
    })

    it('walks down with the arrow keys before taking', async () => {
        const wrapper = await openMenu()
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2'])
    })

    it('walks up and wraps to the last row', async () => {
        const wrapper = await openMenu()
        await press('ArrowUp')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['3'])
    })

    it('walks down past the last row back to the first', async () => {
        const wrapper = await openMenu()
        await press('End')
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['1'])
    })

    it('jumps to the ends with Home and End', async () => {
        const wrapper = await openMenu()
        await press('End')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['3'])
    })

    it('resets the highlight to the first match as the reader types', async () => {
        const wrapper = await openMenu()
        await press('End')
        await searchBox().setValue('ben')
        await flushPromises()
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2'])
    })

    it('closes on Escape without taking anybody', async () => {
        const wrapper = await openMenu()
        await press('Escape')
        expect(panel().exists()).toBe(false)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    it('closes on a press outside it', async () => {
        await openMenu()
        document.body.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true}))
        await flushPromises()
        expect(panel().exists()).toBe(false)
    })

    it('opens from the closed trigger on ArrowDown', async () => {
        const wrapper = mountMenu()
        await wrapper.get('[data-testid="member-select-trigger"]').trigger('keydown', {key: 'ArrowDown'})
        await flushPromises()
        expect(panel().exists()).toBe(true)
    })

    it('offers no empty row unless the call site asks for one', async () => {
        await openMenu()
        expect(panel().text()).not.toContain('Nicht zugewiesen')
    })

    it('offers the empty row where the choice may be emptied', async () => {
        await openMenu({clearable: true})
        expect(panel().text()).toContain('Nicht zugewiesen')
    })

    it('empties the choice through the empty row', async () => {
        const wrapper = await openMenu({clearable: true, modelValue: '2'})
        await page().get('[data-testid="member-select-empty"]').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    })

    it('keeps the empty answer apart from the people, so a story can tell them apart', async () => {
        await openMenu({clearable: true})
        expect(page().findAll('[data-testid="member-select-empty"]')).toHaveLength(1)
        expect(rowTexts()).toHaveLength(PEOPLE.length)
    })

    it('names the empty answer where nobody is not what it means', async () => {
        await openMenu({clearable: true, emptyLabel: 'Für mich selbst'})
        expect(panel().text()).toContain('Für mich selbst')
        expect(panel().text()).not.toContain('Nicht zugewiesen')
    })

    it('offers the kind filter only once more than one kind is on offer', async () => {
        const single = await openMenu({userTypes: ['MEMBER']})
        expect(page().find('[data-testid="member-select-user-type"]').exists()).toBe(false)
        single.unmount()
        await openMenu({userTypes: ['MEMBER', 'GUARDIAN']})
        expect(page().find('[data-testid="member-select-user-type"]').exists()).toBe(true)
    })

    it('opens on the kind the screen is really asking for', async () => {
        await openMenu({userTypes: ['MEMBER', 'GUARDIAN'], openingUserType: 'GUARDIAN'})
        expect(rowTexts()).toHaveLength(1)
        expect(rowTexts()[0]).toContain('Anna Zimmer')
    })

    it('adds rather than replaces when it takes several', async () => {
        const wrapper = await openMenu({multiple: true, selected: ['1']})
        await page().findAll('[data-testid="member-select-option"]')[1]!.trigger('click')
        expect(wrapper.emitted('update:selected')?.at(-1)).toEqual([['1', '2']])
        expect(panel().exists(), 'a menu that takes several stays open').toBe(true)
    })

    it('takes somebody back off the list when they are clicked again', async () => {
        const wrapper = await openMenu({multiple: true, selected: ['1', '2']})
        await page().findAll('[data-testid="member-select-option"]')[0]!.trigger('click')
        expect(wrapper.emitted('update:selected')?.at(-1)).toEqual([['2']])
    })

    it('marks who is chosen for a screen reader', async () => {
        await openMenu({multiple: true, selected: ['2']})
        const chosen = page().findAll('[role="option"][aria-selected="true"]')
        expect(chosen.map(row => row.text())).toEqual([expect.stringContaining('Ben Müller')])
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
        await openMenu({members: [], searchFn})
        await new Promise(resolve => setTimeout(resolve, 0))
        await flushPromises()
        expect(searchFn).toHaveBeenCalledWith('')
        expect(rowTexts()).toHaveLength(3)
    })

    it('puts a name to a choice made before it was drawn', async () => {
        const resolveFn = vi.fn().mockResolvedValue(PEOPLE[1])
        const wrapper = mountMenu({members: [], searchFn: vi.fn().mockResolvedValue([]), resolveFn, modelValue: '1'})
        await new Promise(resolve => setTimeout(resolve, 0))
        expect(resolveFn).toHaveBeenCalledWith('1')
        expect(wrapper.get('[data-testid="member-select-trigger"]').text()).toContain('Anna Zimmer')
    })
})
