/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ResultDimension, ResultMatch, type ResultFilterState, type ResultGroupingState} from '@/api/forms'
import type {FormResultQuery} from '@/api/generated/schema'

/** Where age brackets start unless the reader chooses: under 14, 14-17, 18-26, 27-39, 40-59, 60+. */
export const DEFAULT_AGE_BOUNDS = [14, 18, 27, 40, 60]

/** The key of the group of respondents with no value for what the results are grouped by. */
export const NO_VALUE_GROUP = 'none'

/** A filter that lets everybody through. */
export function emptyFilter(): ResultFilterState {
    return {
        userTypes: [],
        groupIds: [],
        groupMatch: ResultMatch.ANY,
        tagIds: [],
        tagMatch: ResultMatch.ANY,
        fields: [],
        ageFrom: null,
        ageTo: null,
    }
}

/** Whether a filter restricts anything at all. */
export function filterActive(filter: ResultFilterState): boolean {
    return filter.userTypes.length > 0
        || filter.groupIds.length > 0
        || filter.tagIds.length > 0
        || filter.fields.length > 0
        || filter.ageFrom !== null
        || filter.ageTo !== null
}

/** A grouping by a dimension, with every group shown and the default brackets where they apply. */
export function groupingBy(by: ResultGroupingState['by'], fieldId: number | null = null): ResultGroupingState {
    return {by, fieldId, only: [], bounds: by === ResultDimension.AGE ? [...DEFAULT_AGE_BOUNDS] : []}
}

/** What to ask the server for, or null when neither a filter nor a grouping is set. */
export function toQuery(filter: ResultFilterState, grouping: ResultGroupingState | null): FormResultQuery | null {
    const active = filterActive(filter)
    if (!active && !grouping) return null
    return {filter: active ? filter : null, groupBy: grouping}
}

/** Bracket starts as a reader types them: "14, 18 27" becomes 14, 18, 27. */
export function parseBounds(text: string): number[] {
    const numbers = text.split(/[\s,;]+/)
        .map(part => Number.parseInt(part, 10))
        .filter(n => Number.isFinite(n))
    return [...new Set(numbers)].sort((a, b) => a - b)
}

interface StoredView {
    filter: ResultFilterState
    grouping: ResultGroupingState | null
}

/**
 * The view as a value for the address bar, so a filtered and grouped view can be bookmarked or sent
 * on. Nothing is written for the plain view.
 */
export function encodeView(filter: ResultFilterState, grouping: ResultGroupingState | null): string | undefined {
    if (!toQuery(filter, grouping)) return undefined
    return JSON.stringify({filter, grouping} satisfies StoredView)
}

/** Reads a view back from the address bar, falling back to the plain view for anything unreadable. */
export function decodeView(value: unknown): StoredView {
    const plain = {filter: emptyFilter(), grouping: null}
    if (typeof value !== 'string' || !value) return plain
    try {
        const parsed = JSON.parse(value) as Partial<StoredView>
        const grouping = parsed.grouping && Object.values(ResultDimension).includes(parsed.grouping.by)
            ? {...groupingBy(parsed.grouping.by), ...parsed.grouping}
            : null
        return {filter: {...emptyFilter(), ...(parsed.filter ?? {})}, grouping}
    } catch {
        return plain
    }
}
