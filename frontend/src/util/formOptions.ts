/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FormOption} from '@/api/forms'

const KEY_LENGTH = 8
const KEY_ALPHABET = '0123456789abcdefghijklmnopqrstuvwxyz'

/** Where a question keeps its keyed options: a choice and a ranking under `options`, a Likert grid under `statements`. */
export type OptionField = 'options' | 'statements'

function isOption(value: unknown): value is FormOption {
    if (typeof value !== 'object' || value === null) return false
    const candidate = value as Record<string, unknown>
    return typeof candidate.key === 'string' && typeof candidate.label === 'string'
}

/**
 * The keyed options a question's config holds, in their order. Anything in the list that is not a
 * keyed option is left out rather than drawn as a blank row.
 */
export function optionsOf(config: Record<string, unknown>, field: OptionField = 'options'): FormOption[] {
    const list = config[field]
    return Array.isArray(list) ? list.filter(isOption) : []
}

/**
 * The keys of every option a config holds, whichever kind of question it belongs to: the options of
 * a choice or ranking, the statements of a Likert grid, none for any other kind.
 */
export function optionKeysOf(config: Record<string, unknown>): string[] {
    return [...optionsOf(config, 'options'), ...optionsOf(config, 'statements')].map(option => option.key)
}

/** The label of the option with this key, or undefined where the question has no such option. */
export function optionLabel(options: readonly FormOption[], key: string): string | undefined {
    return options.find(option => option.key === key)?.label
}

/**
 * A fresh key for an option added to the given ones: eight random characters, drawn again in the
 * unlikely case they are already taken within the question.
 */
export function newOptionKey(taken: readonly FormOption[]): string {
    return freshKey(new Set(taken.map(option => option.key)))
}

/**
 * Eight random characters that are not among the given keys, for anything a form names by a key of
 * its own: an option, a statement, a page.
 */
export function freshKey(used: ReadonlySet<string>): string {
    let key = ''
    do {
        const bytes = crypto.getRandomValues(new Uint8Array(KEY_LENGTH))
        key = Array.from(bytes, byte => KEY_ALPHABET[byte % KEY_ALPHABET.length]).join('')
    } while (used.has(key))
    return key
}

/** A new, empty option for the given list, with a key of its own. */
export function blankOption(taken: readonly FormOption[]): FormOption {
    return {key: newOptionKey(taken), label: ''}
}

/**
 * Options with the given labels, keyed by position the way options that existed before keys were
 * given theirs (`o0`, `o1`, ...). For options written once in code, such as sample data; the editor
 * gives every new option a random key.
 */
export function numberedOptions(...labels: string[]): FormOption[] {
    return labels.map((label, index) => ({key: `o${index}`, label}))
}
