/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DEFAULT_LOCALE, translator} from '@/util/translatorState'

/**
 * The language the interface is shown in, as a BCP 47 tag every `Intl` API and `toLocaleString`
 * accepts.
 *
 * <p>Dates, numbers, month and weekday names and the order of names all follow it, so that the day
 * the interface speaks another language they change with it instead of staying German beside it.
 * Code outside a component reads it here; a component may equally take `locale` from `useI18n()`.
 */
export function activeLocale(): string {
    return translator.locale()
}

function collatorOf(locale: string, numeric: boolean): Intl.Collator {
    return new Intl.Collator(locale, {sensitivity: 'base', numeric})
}

/** The collators of the default language, with and without numeric ordering. */
const DEFAULT_COLLATORS = {
    text: collatorOf(DEFAULT_LOCALE, false),
    numeric: collatorOf(DEFAULT_LOCALE, true),
}

/**
 * Compares two texts the way a reader of the interface language orders them, ignoring case and
 * accents. With `numeric`, digits compare by their value, so "Fach 10" follows "Fach 9".
 *
 * <p>A collator is costly to build and cheap to reuse, and a sort asks for one on every comparison,
 * so the default language's are built once. That is the language the server always renders in and the
 * only one the interface speaks today; another one gets a collator built for the comparison.
 */
export function compareText(left: string, right: string, numeric = false): number {
    const locale = activeLocale()
    const collator = locale === DEFAULT_LOCALE
        ? (numeric ? DEFAULT_COLLATORS.numeric : DEFAULT_COLLATORS.text)
        : collatorOf(locale, numeric)
    return collator.compare(left, right)
}
