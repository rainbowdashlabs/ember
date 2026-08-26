/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** One CSV column and the member field it is written to. */
export interface ColumnMapping {
    csvColumn: string
    target: string
    mergeOrder: number
    mergeSeparator: string
    valueMap: Record<string, string>
    splitChar: string
    splitIndex: number
}

export interface ContactPreview {
    name: string
    phone: string
    email: string
}

export interface MemberPreview {
    firstName: string
    lastName: string
    email: string
    group: string
    profileFields: Record<string, string>
    contacts: ContactPreview[]
    /** Where the row came from in the file, which is what striking one out refers to. */
    row: number
    /** Whether it is struck out, in which case the import walks past it. */
    ignored: boolean
}

export interface PreviewResult {
    members: MemberPreview[]
    warnings: string[]
}

export const SKIP_TARGET = 'skip'

export function createColumnMapping(csvColumn: string, target: string, mergeOrder: number): ColumnMapping {
    return {csvColumn, target, mergeOrder, mergeSeparator: ' ', valueMap: {}, splitChar: '', splitIndex: 0}
}
