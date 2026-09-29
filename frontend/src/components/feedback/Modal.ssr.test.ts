/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {createSSRApp, h} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createI18n} from 'vue-i18n'
import Modal from './Modal.vue'

/**
 * The cluster pages are rendered on the server and hold dialogs, so a dialog has to render there
 * without reaching for a document that is not there, open or closed.
 */
describe('Modal on the server', () => {
    it.each([false, true])('renders without a document when open is %s', async (open) => {
        const app = createSSRApp({
            render: () => [h('p', 'Seite'), h(Modal, {modelValue: open}, () => 'Inhalt')],
        })
        app.use(createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}}}))

        const html = await renderToString(app)

        expect(html).toContain('Seite')
        expect(html).not.toContain('Inhalt')
    })
})
