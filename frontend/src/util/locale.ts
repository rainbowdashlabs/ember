/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {translator} from '@/util/translatorState'

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

const collators = new Map<string, Intl.Collator>()

/**
 * Compares two texts the way a reader of the interface language orders them, ignoring case and
 * accents. With `numeric`, digits compare by their value, so "Fach 10" follows "Fach 9".
 *
 * <p>A collator is costly to build and cheap to reuse, so one is kept per language and option.
 */
export function compareText(left: string, right: string, numeric = false): number {
    const locale = activeLocale()
    const key = `${locale}|${numeric}`
    let collator = collators.get(key)
    if (!collator) {
        collator = new Intl.Collator(locale, {sensitivity: 'base', numeric})
        collators.set(key, collator)
    }
    return collator.compare(left, right)
}
