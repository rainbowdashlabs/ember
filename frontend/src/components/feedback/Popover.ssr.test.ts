/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment node
import {describe, expect, it} from 'vitest'
import {createSSRApp, h} from 'vue'
import {renderToString} from 'vue/server-renderer'
import Popover from './Popover.vue'

describe('Popover on the server', () => {
    it('renders closed without reaching for a document that is not there', async () => {
        const app = createSSRApp({
            render: () => h(Popover, {label: 'Spalten'}, {
                trigger: () => h('button', 'Spalten'),
                default: () => 'Inhalt',
            }),
        })

        const html = await renderToString(app)

        expect(html).toContain('Spalten')
        expect(html).not.toContain('Inhalt')
    })
})
