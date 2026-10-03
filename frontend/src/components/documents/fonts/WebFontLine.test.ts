/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, mount} from '@vue/test-utils'
import {WebFontFormat, type WebFontView} from '@/api/generated/schema'
import WebFontLine from './WebFontLine.vue'

enableAutoUnmount(afterEach)

const WEB: WebFontView = {fileName: 'haus.woff2', format: WebFontFormat.WOFF2, sizeBytes: 2048, uploadedAt: '2026-10-03T10:00:00Z'}

/** A style says whether it has a web version and offers to upload, replace or remove it. */
describe('WebFontLine', () => {
    it('offers an upload where the style has no web version', async () => {
        const wrapper = mount(WebFontLine, {props: {web: null}})

        expect(wrapper.text()).toContain('Keine Webfassung')
        expect(wrapper.find('[data-testid="web-font-remove"]').exists()).toBe(false)
        await wrapper.get('[data-testid="web-font-upload"]').trigger('click')
        expect(wrapper.emitted('upload')).toHaveLength(1)
    })

    it('names the web version and offers to replace and remove it', async () => {
        const wrapper = mount(WebFontLine, {props: {web: WEB}})

        expect(wrapper.text()).toContain('haus.woff2')
        await wrapper.get('[data-testid="web-font-replace"]').trigger('click')
        await wrapper.get('[data-testid="web-font-remove"]').trigger('click')
        expect(wrapper.emitted('upload')).toHaveLength(1)
        expect(wrapper.emitted('remove')).toHaveLength(1)
    })
})
