/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {vi} from 'vitest'
import {createSSRApp, defineComponent, h, type VNodeChild} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createI18n} from 'vue-i18n'
import {createNuxtApp} from '#app'
import deDE from '@/i18n/de-DE'

/**
 * Rendering the way the server renders one request, for tests in the `node` environment.
 *
 * <p>Every call is a request of its own: a new app with its own Nuxt instance, so `useState` holds
 * one value per call just as it holds one per request on the server, while module state is shared
 * between calls just as it is shared by every request of one server process. Rendering twice in a
 * row is therefore how a test shows that nothing one reader's page wrote reaches the next reader.
 *
 * <p>A warning from Vue fails the render. A write to browser state on the server is only a warning,
 * and one nobody would read in a server log.
 *
 * @param render what the page draws
 * @return the markup the server would send
 */
export async function renderRequest(render: () => VNodeChild): Promise<string> {
    const app = createSSRApp({render})
    attachNuxt(app)
    app.use(createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}}))
    app.component('font-awesome-icon', {render: () => h('span')})
    app.component('NuxtLink', defineComponent({
        props: {to: {type: [String, Object], default: ''}},
        setup: (_props, {slots}) => () => h('a', slots.default?.()),
    }))

    const warnings: string[] = []
    app.config.warnHandler = message => warnings.push(message)
    const consoleWarn = vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
        warnings.push(args.map(String).join(' '))
    })
    try {
        const html = await renderToString(app)
        if (warnings.length) throw new Error(`The server render warned:\n${warnings.join('\n')}`)
        return html
    } finally {
        consoleWarn.mockRestore()
    }
}

/**
 * Gives the app the Nuxt instance a server request would have.
 *
 * <p>Nuxt's own flags say browser in every test environment, so creating the instance looks for the
 * payload a server left in the page. There is no page here and no payload to find; the lookup is
 * answered with nothing, and the window it needed is gone again before anything renders.
 */
function attachNuxt(app: ReturnType<typeof createSSRApp>): void {
    vi.stubGlobal('window', {addEventListener: () => undefined})
    try {
        createNuxtApp({vueApp: app})
    } finally {
        vi.unstubAllGlobals()
    }
}
