/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * German is both the language shown and the language fallen back to.
 *
 * <p>The English file is deliberately partial, so every key it does not carry is read in German
 * rather than shown as its own name. That is what lets English grow a screen at a time instead of
 * waiting until all of it is translated.
 *
 * <p>The messages are not part of this configuration. `@nuxtjs/i18n` reads the locale files named in
 * `nuxt.config.ts` and loads a language only once it is shown: the server renders a public page with
 * the German it loaded for that request, and the browser fetches the same messages as one cacheable
 * file instead of carrying them in every page's scripts. Every request on the server gets an
 * instance of its own, so a language one request switches to never reaches the next.
 *
 * <p>Nothing switches the locale yet: a station carries the language its documents are written in,
 * and the interface will follow it once that reaches the browser. Until then this is the mechanism
 * without the switch, which is the half worth having first.
 */
export default defineI18nConfig(() => ({
    legacy: false,
    fallbackLocale: 'de-DE',
}))
