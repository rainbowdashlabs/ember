/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {defineComponent, h} from 'vue'
import {renderRequest} from '@/test/ssr'
import {useConsentGuard} from './useConsentGuard'
import {claimPageHeader, usePageHeader} from './usePageHeader'
import {useSidebarCollapse} from './useSidebarCollapse'

/** A page that changes shared state while it sets up when told to, and draws what it then holds. */
function page(write: () => void, read: () => string) {
    return defineComponent({
        props: {writes: {type: Boolean, default: false}},
        setup(props) {
            if (props.writes) write()
            return () => h('p', read())
        },
    })
}

/**
 * The shared state the public pages reach, rendered as two requests of one server process. What the
 * first reader's page wrote must not be what the second reader's page draws.
 */
describe('state shared by the public pages', () => {
    it('keeps a demand to agree again to the request that made it', async () => {
        const Guarded = page(
            () => useConsentGuard().setNeedsReconsent(true),
            () => String(useConsentGuard().needsReconsent.value),
        )

        expect(await renderRequest(() => h(Guarded, {writes: true}))).toBe('<p>true</p>')
        expect(await renderRequest(() => h(Guarded))).toBe('<p>false</p>')
    })

    it('keeps a collapsed sidebar to the request that collapsed it', async () => {
        const Sidebar = page(
            () => useSidebarCollapse().setCollapsed(true),
            () => String(useSidebarCollapse().collapsed.value),
        )

        expect(await renderRequest(() => h(Sidebar, {writes: true}))).toBe('<p>true</p>')
        expect(await renderRequest(() => h(Sidebar))).toBe('<p>false</p>')
    })

    it('keeps a page header to the page that set it', async () => {
        const Header = page(
            () => claimPageHeader().set('Wache Nord', 'Einsätze'),
            () => usePageHeader().title.value,
        )

        expect(await renderRequest(() => h(Header, {writes: true}))).toBe('<p>Wache Nord</p>')
        expect(await renderRequest(() => h(Header))).toBe('<p></p>')
    })
})
