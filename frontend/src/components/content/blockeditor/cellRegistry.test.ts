/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {LAYOUT_KINDS} from '@/api/pageManage'
import {cellProps, LAYOUT_CELLS} from './cellRegistry'

/**
 * The one table the page renderer and the editor panel both read a layout cell type from.
 *
 * @vitest-environment happy-dom
 */
describe('LAYOUT_CELLS', () => {
    it('has a renderer and an editor for every layout kind', () => {
        for (const kind of LAYOUT_KINDS) {
            expect(LAYOUT_CELLS[kind].render.component, kind).toBeTruthy()
            expect(LAYOUT_CELLS[kind].editor.component, kind).toBeTruthy()
        }
    })

    it('binds only the inputs a component takes', () => {
        const bound = cellProps(LAYOUT_CELLS.NEWS_TEASER.render, {limit: 3}, {
            content: 'body',
            stationUid: 'station',
            timezone: 'Europe/Berlin',
            kind: 'NEWS_TEASER',
        })

        expect(bound).toEqual({config: {limit: 3}, stationUid: 'station', timezone: 'Europe/Berlin'})
    })
})
