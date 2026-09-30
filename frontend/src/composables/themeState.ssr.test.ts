/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {renderRequest} from '@/test/ssr'
import {themeRepainted} from '@/util/themeState'
import {usePride} from './usePride'
import {useTheme} from './useTheme'
import {useThemePaint} from './useThemePaint'

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
 * The theme and the pride flag rendered as two requests of one server process. What the first
 * reader's page set must not be what the second reader's page draws.
 */
describe('theme on the server', () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    it('keeps a station colour set to the request that set it', async () => {
        const Themed = page(
            () => useTheme().applyCustomColors('{"primary":"#123456"}'),
            () => JSON.stringify(useTheme().customThemeColors.value),
        )

        expect(await renderRequest(() => h(Themed, {writes: true}))).toBe('<p>{&quot;primary&quot;:&quot;#123456&quot;}</p>')
        expect(await renderRequest(() => h(Themed))).toBe('<p>null</p>')
    })

    it('refuses a repaint during a server render and paints every request light', async () => {
        const Painted = page(themeRepainted, () => String(useThemePaint().dark.value))
        const darkRoot = {documentElement: {classList: {contains: () => true}}}
        Object.assign(globalThis, {document: darkRoot})
        try {
            await expect(renderRequest(() => h(Painted, {writes: true}))).rejects.toThrow(/server render warned/)
        } finally {
            Reflect.deleteProperty(globalThis, 'document')
        }

        expect(await renderRequest(() => h(Painted))).toBe('<p>false</p>')
    })

    it('keeps a forced pride flag to the request that forced it', async () => {
        vi.useFakeTimers({toFake: ['Date'], now: new Date('2026-09-15T12:00:00Z')})
        const Flag = page(
            () => usePride().setForcePrideFlag(true),
            () => String(usePride().prideActive.value),
        )

        expect(await renderRequest(() => h(Flag, {writes: true}))).toBe('<p>true</p>')
        expect(await renderRequest(() => h(Flag))).toBe('<p>false</p>')
    })

    it('stops showing the pride flag when the month is over', async () => {
        const Flag = page(() => undefined, () => usePride().prideVariant.value)

        vi.useFakeTimers({toFake: ['Date'], now: new Date('2026-07-31T12:00:00Z')})
        expect(await renderRequest(() => h(Flag))).toBe('<p>banner</p>')

        vi.setSystemTime(new Date('2026-08-01T12:00:00Z'))
        expect(await renderRequest(() => h(Flag))).toBe('<p>text</p>')
    })
})
