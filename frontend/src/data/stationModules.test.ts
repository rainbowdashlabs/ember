/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {StationModule} from '@/api/generated/schema'
import deDE from '@/i18n/de-DE'
import {STATION_MODULE_OPTIONS} from './stationModules'

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
