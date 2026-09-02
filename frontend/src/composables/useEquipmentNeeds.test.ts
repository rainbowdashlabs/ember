/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent, ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {useEquipmentNeeds} from './useEquipmentNeeds'

const coverage = vi.fn()
const addNeed = vi.fn()
const removeNeed = vi.fn()

vi.mock('@/api', () => ({
    equipment: {
        coverage: (...args: unknown[]) => coverage(...args),
        add: (...args: unknown[]) => addNeed(...args),
        remove: (...args: unknown[]) => removeNeed(...args),
    },
    inventory: {
        listInventories: async () => [],
        listAllItems: async () => [],
    },
    inventoryArts: {
        listArts: async () => [],
    },
}))

/** A refusal as the server sends it, with whatever it chose to say about it. */
function refusal(message?: string) {
    return message ? {response: {data: {message}}} : {response: {status: 403, data: {}}}
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function needsFor(date: string | null) {
    let api: ReturnType<typeof useEquipmentNeeds> | null = null
    mount(defineComponent({
        setup() {
            api = useEquipmentNeeds(ref(7), ref(date))
            return () => null
        },
    }))
    return api as unknown as ReturnType<typeof useEquipmentNeeds>
}

function line() {
    return {
        kind: 'art' as const,
        itemId: '',
        artId: '3',
        inventoryId: '',
        quantity: 2,
        leadHours: 24,
        trailHours: 24,
        thisEveningOnly: false,
    }
}

describe('useEquipmentNeeds', () => {
    beforeEach(() => {
        coverage.mockReset()
        addNeed.mockReset()
        removeNeed.mockReset()
        coverage.mockResolvedValue([])
    })

    /**
     * A question that could not be asked and an appointment that needs nothing look the same on the
     * screen: an empty list. Only one of them is an answer.
     */
    it('says a coverage that could not be read went wrong, rather than showing it as empty', async () => {
        coverage.mockRejectedValue(refusal('Kein Zugriff auf das Inventar'))
        const needs = needsFor('2026-09-04')

        await needs.loadCoverage()

        expect(needs.coverage.value, 'nothing was read').toEqual([])
        expect(needs.error.value, 'and the screen is told so').toBe('Kein Zugriff auf das Inventar')
    })

    it('keeps a coverage that was read clear of an earlier failure', async () => {
        coverage.mockRejectedValueOnce(refusal('Kein Zugriff auf das Inventar'))
        const needs = needsFor('2026-09-04')
        await needs.loadCoverage()

        await needs.loadCoverage()

        expect(needs.error.value).toBe('')
    })

    /**
     * A refusal without a sentence of its own used to leave the dialog blank, so pressing the button
     * again was the only thing left to try.
     */
    it('names a failure the server said nothing about', async () => {
        addNeed.mockRejectedValue(refusal())
        const needs = needsFor('2026-09-04')

        const done = await needs.add(line())

        expect(done, 'the line was not written').toBe(false)
        expect(needs.saveError.value, 'and the dialog has something to show').not.toBe('')
    })

    it('repeats what the server said about a line it would not take', async () => {
        addNeed.mockRejectedValue(refusal('Diese Art gibt es hier nicht'))
        const needs = needsFor('2026-09-04')

        await needs.add(line())

        expect(needs.saveError.value).toBe('Diese Art gibt es hier nicht')
    })

    it('leaves nothing of the last failure behind once a line is written', async () => {
        addNeed.mockRejectedValueOnce(refusal('Diese Art gibt es hier nicht'))
        const needs = needsFor('2026-09-04')
        await needs.add(line())

        addNeed.mockResolvedValue(undefined)
        const done = await needs.add(line())

        expect(done).toBe(true)
        expect(needs.saveError.value).toBe('')
    })

    /** Removing a line is a request like any other, and it is refused like any other. */
    it('says why a line could not be removed', async () => {
        removeNeed.mockRejectedValue(refusal('Die Zeile ist bereits ausgegeben'))
        const needs = needsFor('2026-09-04')

        await needs.remove(12)

        expect(needs.saveError.value).toBe('Die Zeile ist bereits ausgegeben')
    })

    it('reads nothing without an evening to read for', async () => {
        const needs = needsFor(null)

        await needs.loadCoverage()

        expect(coverage).not.toHaveBeenCalled()
        expect(needs.error.value).toBe('')
    })
})
