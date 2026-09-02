/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef, type Ref, type WritableComputedRef} from 'vue'

/** How many rows a typeahead offers at once, which is as many as anybody reads before typing more. */
const RESULT_LIMIT = 25

/**
 * A search over a list that is already to hand, in the shape a search picker expects.
 *
 * <p>Every word typed has to appear somewhere in the searchable text of a row, so two words narrow
 * rather than widen. The list is capped, because a typeahead offering four hundred rows is the
 * dropdown it replaced.
 *
 * @param entries    the rows to search
 * @param textOf     everything about a row that a word may match, joined
 * @returns a search function for a search picker
 */
export function listSearch<T>(
    entries: ComputedRef<T[]> | Ref<T[]>,
    textOf: (entry: T) => string,
): (query: string) => Promise<T[]> {
    return async (query: string) => {
        const words = query.toLowerCase().split(/\s+/).filter(Boolean)
        return entries.value
            .filter(entry => {
                const text = textOf(entry).toLowerCase()
                return words.every(word => text.includes(word))
            })
            .slice(0, RESULT_LIMIT)
    }
}

/**
 * A search picker speaks in strings and an identifier out of the database is a number, which is the
 * one translation between them.
 *
 * @param model the numeric model
 * @returns the same model read and written as a string
 */
export function numericPickerModel(
    model: Ref<number | null | undefined>,
): WritableComputedRef<string | null> {
    return computed({
        get: () => (model.value != null ? String(model.value) : null),
        set: value => {
            model.value = value != null && value !== '' ? Number(value) : null
        },
    })
}
