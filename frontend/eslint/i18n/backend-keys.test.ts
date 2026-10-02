/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import rule from './backend-keys.mjs'
import {fixture, fixtureSource, GERMAN, localeRuleTester} from './fixture'

/**
 * Holding the translations to what the backend declares.
 *
 * @vitest-environment node
 *
 * <p>A section that mirrors an enum is only ever reached through a key built at runtime, so a new
 * constant without a translation shows its key to a reader and a removed one leaves a translation
 * behind that nothing can reach. Neither shows up anywhere but here.
 */
const OPTIONS = {
    german: GERMAN,
    sections: [
        {enumFiles: [fixture('java/Kinds.java')], prefix: 'kinds'},
        {
            enumFiles: [fixture('java/refusal/FormRefusal.java'), fixture('java/refusal/GeneralRefusal.java')],
            prefix: 'refusal',
            reader: 'refusal-codes',
        },
    ],
    keyFiles: [fixture('java/Sender.java')],
}

localeRuleTester().run('i18n-backend-keys', rule, {
    valid: [
        {
            name: 'the help centre holds no section, so nothing is reported there',
            filename: fixture('src/i18n/de-DE.helpcenter.ts'),
            code: fixtureSource('src/i18n/de-DE.helpcenter.ts'),
            options: [OPTIONS],
        },
    ],
    invalid: [
        {
            name: 'reports a constant without a translation, a translation without a constant and a key the backend sends',
            filename: fixture('src/i18n/de-DE.ts'),
            code: fixtureSource('src/i18n/de-DE.ts'),
            options: [OPTIONS],
            errors: [
                {message: `i18n key 'sent.missing' sent by ${fixture('java/Sender.java')} is not defined.`, line: 3},
                {message: "Missing translation for enum value REMOTE: 'kinds.REMOTE'.", line: 14},
                {message: 'Stale kinds entry GONE: no enum value matches it.', line: 16},
            ],
        },
        {
            name: 'reads refusals by their code, in the file that holds them',
            filename: fixture('src/i18n/refusals/forms.ts'),
            code: fixtureSource('src/i18n/refusals/forms.ts'),
            options: [OPTIONS],
            errors: [
                {message: "Missing translation for enum value F-003: 'refusal.F-003'."},
                {message: 'Stale refusal entry F-002: no enum value matches it.', line: 5},
            ],
        },
        {
            name: 'reports a stale refusal in the area file holding it, and a missing one only in the first',
            filename: fixture('src/i18n/refusals/general.ts'),
            code: fixtureSource('src/i18n/refusals/general.ts'),
            options: [OPTIONS],
            errors: [{message: 'Stale refusal entry G-009: no enum value matches it.', line: 5}],
        },
        {
            name: 'reports a Java file that is not there',
            filename: fixture('src/i18n/de-DE.ts'),
            code: fixtureSource('src/i18n/de-DE.ts'),
            options: [{german: GERMAN, sections: [{enumFiles: [fixture('java/Gone.java')], prefix: 'kinds'}]}],
            errors: [
                {message: `Backend source not found: ${fixture('java/Gone.java')}.`},
                {message: 'Stale kinds entry LOCAL: no enum value matches it.'},
                {message: 'Stale kinds entry GONE: no enum value matches it.'},
            ],
        },
    ],
})
