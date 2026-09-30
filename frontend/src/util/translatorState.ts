/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
let translate: (key: string) => string = key => key

/**
 * Translation for code that runs outside any component, such as the request client's interceptors,
 * where no Nuxt app can be asked for its i18n instance.
 *
 * <p>Only the browser fills it in, from its one app's instance, through `plugins/translator.client.ts`.
 * The server keeps an instance per request and never hands one to module state, which is what keeps one
 * request's language away from the next; there a key comes back as itself, and nothing on the server
 * shows the toasts that would carry it.
 */
export const translator = {
    t: (key: string): string => translate(key),
}

/** Hands {@link translator} the browser's translate function. */
export function setTranslator(t: (key: string) => string): void {
    translate = t
}
