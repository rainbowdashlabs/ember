/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import rule from './unused-keys.mjs'
import {fixture, fixtureSource, GERMAN, localeRuleTester} from './fixture'

/**
 * Which translations count as referenced.
 *
 * @vitest-environment node
 *
 * <p>Keys reach `t()` in more ways than being written into a call, and every one the rule misses
 * is a translation it would have somebody delete from under a screen that shows it. The fixture's
 * sources use each of those ways once, and the German file defines one key none of them reaches.
 */
const OPTIONS = {
    sources: fixture('src'),
    german: GERMAN,
    translations: [fixture('src/i18n/en.ts')],
    backendKeyFiles: [fixture('java/Sender.java')],
}

localeRuleTester().run('i18n-unused-keys', rule, {
    valid: [
        {
            name: 'every refusal is reached through the key built from its code',
            filename: fixture('src/i18n/de-DE.refusals.ts'),
            code: fixtureSource('src/i18n/de-DE.refusals.ts'),
            options: [OPTIONS],
        },
        {
            name: 'a file the rule is not told about is left alone',
            filename: fixture('src/i18n/other.ts'),
            code: 'export default {nobody: {uses: \'this\'}}',
            options: [OPTIONS],
        },
    ],
    invalid: [
        {
            name: 'reports the one German key nothing reaches, and nothing the sources reach another way',
            filename: fixture('src/i18n/de-DE.ts'),
            code: fixtureSource('src/i18n/de-DE.ts'),
            options: [OPTIONS],
            errors: [{message: "i18n key 'common.unused' is never referenced."}],
        },
        {
            name: 'reads the help centre under the key it is merged in under',
            filename: fixture('src/i18n/de-DE.helpcenter.ts'),
            code: fixtureSource('src/i18n/de-DE.helpcenter.ts'),
            options: [OPTIONS],
            errors: [{message: "i18n key 'helpCenter.orphan' is never referenced."}],
        },
        {
            name: 'reports a key another language defines and German does not',
            filename: fixture('src/i18n/en.ts'),
            code: fixtureSource('src/i18n/en.ts'),
            options: [OPTIONS],
            errors: [{message: "i18n key 'extra.only' is not defined in German, which every other language falls back to."}],
        },
        {
            name: 'deletes the key with its line when asked to',
            filename: fixture('src/i18n/de-DE.ts'),
            code: fixtureSource('src/i18n/de-DE.ts'),
            options: [{...OPTIONS, enableFix: true}],
            output: fixtureSource('src/i18n/de-DE.ts').replace("        unused: 'Nie benutzt',\n", ''),
            errors: [{message: "i18n key 'common.unused' is never referenced."}],
        },
    ],
})
