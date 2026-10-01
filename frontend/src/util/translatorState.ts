/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {browserShallowRef} from '@/util/browserState'

type Translate = (key: string, params?: Record<string, unknown>) => string

/** The browser's translate function and the language it speaks. */
interface BrowserTranslation {
    t: Translate
    locale: () => string
}

const DEFAULT_LOCALE = 'de-DE'

/** What the client plugin handed over, or null before it has, and always on the server. */
const handedOver = browserShallowRef<BrowserTranslation | null>(null)

/**
 * Translation for code that runs outside any component, such as the request client's interceptors,
 * where no Nuxt app can be asked for its i18n instance.
 *
 * <p>Only the browser fills it in, from its one app's instance, through `plugins/translator.client.ts`.
 * The server keeps an instance per request and never hands one to module state, which is what keeps one
 * request's language away from the next; there a key comes back as itself, and nothing on the server
 * shows the toasts that would carry it. The language falls back to the default one on the server, which
 * is the only language the interface speaks in full.
 */
export const translator = {
    t: (key: string, params?: Record<string, unknown>): string => handedOver.value?.t(key, params) ?? key,
    locale: (): string => handedOver.value?.locale() ?? DEFAULT_LOCALE,
}

/** Hands {@link translator} the browser's translate function and the language it speaks. */
export function setTranslator(t: Translate, locale: () => string): void {
    handedOver.value = {t, locale}
}
