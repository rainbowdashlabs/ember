/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {StationModule} from '@/api/generated/schema'
import deDE from '@/i18n/de-DE'
import {STATION_MODULE_OPTIONS, moduleRuns, parentAllows} from './stationModules'

function translated(key: string): unknown {
    return key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], deDE)
}

/**
 * The one list of modules. Every screen that lists modules reads it, so a module missing here is
 * missing everywhere at once, which is what the tests guard.
 */
describe('STATION_MODULE_OPTIONS', () => {
    it('offers every module a station can have, each once', () => {
        const offered = STATION_MODULE_OPTIONS.map(option => option.value)
        expect([...offered].sort()).toEqual(Object.values(StationModule).sort())
    })

    it('includes the documents, which an association and the setup could not reach before', () => {
        expect(STATION_MODULE_OPTIONS.map(option => option.value)).toContain(StationModule.DOCUMENTS)
    })

    it('names and describes every module in words that exist', () => {
        for (const option of STATION_MODULE_OPTIONS) {
            expect(translated(option.labelKey), option.labelKey).toEqual(expect.any(String))
            expect(translated(option.descriptionKey), option.descriptionKey).toEqual(expect.any(String))
        }
    })
})

/** The parts of the inventory follow the inventory, which takes them along when it is off. */
describe('module parts', () => {
    /** Where each part of the inventory sits, and under which module. */
    const tree: [StationModule, StationModule, number][] = [
        [StationModule.INVENTORY_MOVEMENTS, StationModule.INVENTORY, 1],
        [StationModule.INVENTORY_LENDING, StationModule.INVENTORY, 1],
        [StationModule.INVENTORY_PROCUREMENT, StationModule.INVENTORY, 1],
        [StationModule.INVENTORY_CHECKS, StationModule.INVENTORY, 1],
        [StationModule.INVENTORY_MEMBER_CHECKS, StationModule.INVENTORY_CHECKS, 2],
        [StationModule.INVENTORY_SELF_CHECKS, StationModule.INVENTORY_MEMBER_CHECKS, 3],
        [StationModule.INVENTORY_CONTAINER_CHECKS, StationModule.INVENTORY_CHECKS, 2],
    ]

    it('lists every part under its module in tree order, indented by how deep it sits', () => {
        const order = STATION_MODULE_OPTIONS.map(option => option.value)
        const inventory = order.indexOf(StationModule.INVENTORY)
        expect(order.slice(inventory + 1, inventory + 1 + tree.length)).toEqual(tree.map(([module]) => module))
        for (const option of STATION_MODULE_OPTIONS) {
            const placed = tree.find(([module]) => module === option.value)
            expect(option.parent).toBe(placed?.[1])
            expect(option.depth).toBe(placed?.[2] ?? 0)
        }
    })

    it('runs a part of a part only while every module above it is on', () => {
        const off = new Set<StationModule>([StationModule.INVENTORY])
        const isOn = (module: StationModule) => !off.has(module)

        expect(moduleRuns(StationModule.INVENTORY_MEMBER_CHECKS, isOn)).toBe(false)
        expect(parentAllows(StationModule.INVENTORY_MEMBER_CHECKS, isOn)).toBe(false)

        off.clear()
        off.add(StationModule.INVENTORY_CHECKS)
        expect(moduleRuns(StationModule.INVENTORY_CONTAINER_CHECKS, isOn)).toBe(false)
        expect(parentAllows(StationModule.INVENTORY_CONTAINER_CHECKS, isOn)).toBe(false)

        off.clear()
        off.add(StationModule.INVENTORY_MEMBER_CHECKS)
        expect(moduleRuns(StationModule.INVENTORY_MEMBER_CHECKS, isOn)).toBe(false)
        expect(moduleRuns(StationModule.INVENTORY_CONTAINER_CHECKS, isOn)).toBe(true)
        expect(moduleRuns(StationModule.INVENTORY_CHECKS, isOn)).toBe(true)
        expect(moduleRuns(StationModule.INVENTORY_SELF_CHECKS, isOn)).toBe(false)
        expect(parentAllows(StationModule.INVENTORY_SELF_CHECKS, isOn)).toBe(false)

        off.clear()
        off.add(StationModule.INVENTORY_CONTAINER_CHECKS)
        expect(moduleRuns(StationModule.INVENTORY_SELF_CHECKS, isOn)).toBe(true)
    })

    it('runs a part only while it and its module are on', () => {
        const off = new Set<StationModule>([StationModule.INVENTORY])
        const isOn = (module: StationModule) => !off.has(module)

        expect(moduleRuns(StationModule.INVENTORY_LENDING, isOn)).toBe(false)
        expect(parentAllows(StationModule.INVENTORY_LENDING, isOn)).toBe(false)
        expect(moduleRuns(StationModule.NEWS, isOn)).toBe(true)
        expect(parentAllows(StationModule.NEWS, isOn)).toBe(true)

        off.clear()
        off.add(StationModule.INVENTORY_LENDING)
        expect(moduleRuns(StationModule.INVENTORY_LENDING, isOn)).toBe(false)
        expect(parentAllows(StationModule.INVENTORY_LENDING, isOn)).toBe(true)
        expect(moduleRuns(StationModule.INVENTORY_CHECKS, isOn)).toBe(true)
    })
})
