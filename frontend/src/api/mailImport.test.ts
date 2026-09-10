/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {isValidSenderPattern, MailImportOutcome, wasImported} from './mailImport'

describe('isValidSenderPattern', () => {
    it('accepts one address', () => {
        expect(isValidSenderPattern('archive@feuerwehr-musterstadt.de')).toBe(true)
        expect(isValidSenderPattern('  Archive@Musterstadt.de  ')).toBe(true)
    })

    it('accepts every address at one domain', () => {
        expect(isValidSenderPattern('*@feuerwehr-musterstadt.de')).toBe(true)
    })

    /**
     * The refusal that matters. This is the check deciding whether a stranger can put files into the
     * station's document store, so nothing wide enough to trust everybody may be typed at all.
     */
    it('refuses anything that would trust everybody', () => {
        expect(isValidSenderPattern('*')).toBe(false)
        expect(isValidSenderPattern('*@*')).toBe(false)
        expect(isValidSenderPattern('*@*.de')).toBe(false)
        expect(isValidSenderPattern('.*')).toBe(false)
        expect(isValidSenderPattern('*@')).toBe(false)
        expect(isValidSenderPattern('archive@*.de')).toBe(false)
    })

    it('refuses what is neither of the two forms', () => {
        expect(isValidSenderPattern('')).toBe(false)
        expect(isValidSenderPattern('   ')).toBe(false)
        expect(isValidSenderPattern('feuerwehr-musterstadt.de')).toBe(false)
        expect(isValidSenderPattern('archive@')).toBe(false)
        expect(isValidSenderPattern('@musterstadt.de')).toBe(false)
        expect(isValidSenderPattern('archive@localhost')).toBe(false)
        expect(isValidSenderPattern('two addresses@musterstadt.de')).toBe(false)
        expect(isValidSenderPattern('a@b@musterstadt.de')).toBe(false)
    })
})

describe('wasImported', () => {
    it('is true only for the outcome that produced a document', () => {
        expect(wasImported(MailImportOutcome.IMPORTED)).toBe(true)
        expect(wasImported(MailImportOutcome.DUPLICATE)).toBe(false)
        expect(wasImported(MailImportOutcome.SENDER_NOT_ALLOWED)).toBe(false)
        expect(wasImported(MailImportOutcome.TOO_LARGE)).toBe(false)
    })
})
