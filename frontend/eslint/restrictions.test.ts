/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import {builtinRules} from 'eslint/use-at-your-own-risk'
import {GENERIC_FAILURE, INLINE_DATE_FORMAT, INLINE_STATE_TYPE, MUTABLE_IDENTITY, STORY_CASTING} from './restrictions.mjs'

/**
 * What each refused shape catches and what it leaves alone.
 *
 * @vitest-environment node
 *
 * <p>A selector reads as nothing like the code it stands against, so the cases are the only honest
 * account of it: too eager and it refuses code that is fine, too shy and the shape grows back.
 */
const tester = new RuleTester({languageOptions: {parser: tsParser}})
const restricted = builtinRules.get('no-restricted-syntax')!

/**
 * The cases for one restriction.
 *
 * @param restriction the selector and its message
 * @param valid code it must leave alone
 * @param invalid code it must report once
 */
function holds(restriction: {selector: string, message: string}, valid: string[], invalid: string[]) {
    tester.run('no-restricted-syntax', restricted, {
        valid: valid.map(code => ({code, options: [restriction]})),
        invalid: invalid.map(code => ({code, options: [restriction], errors: [{message: restriction.message}]})),
    })
}

holds(GENERIC_FAILURE, [
    'failure.value = describeFailure(e, t)',
    "error.value = t('common.errorTitle')",
    "error.value = t('common.error', {name})",
], [
    "error.value = t('common.error')",
    'error.value = t("common.error")',
    "error.value = t( 'common.error' )",
    "error.value = $t('common.error')",
    "error.value = i18n.t('common.error')",
])

holds(INLINE_STATE_TYPE, [
    'const count = ref(0)',
    'const impact = ref<DeleteImpact | null>(null)',
    'const byId = ref<Record<string, number>>({})',
], [
    'const impact = ref<{folders: number}>({folders: 0})',
    'const impact = ref<{folders: number} | null>(null)',
])

holds(INLINE_DATE_FORMAT, [
    'formatDateTime(value)',
    "count.toLocaleString('de-DE')",
], [
    "new Date(value).toLocaleDateString('de-DE')",
    'new Date(value).toLocaleTimeString()',
    "new Date(value).toLocaleString('de-DE', {day: '2-digit'})",
])

holds(STORY_CASTING, [
    'const cast = await castOf(request)',
    'const accounts = await demoAccounts(request)',
], [
    "const member = await accountWith(request, 'MEMBER')",
    'const peers = await fixtures.stationPeers(request)',
])

holds(MUTABLE_IDENTITY, [
    'if (member.id === other.id) return',
    "if (station.name === 'Nord') return",
], [
    'if (member.email === other.email) return',
    'if (email === address) return',
    'if (row.lastName !== person.lastName) return',
])
