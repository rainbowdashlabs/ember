/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {ExportSeparator} from '@/util/exportFormat'

/** Where a chosen column draws its values from. */
export type MemberTableColumnKind = 'BUILTIN' | 'PROFILE_FIELD' | 'REGISTRATION_FIELD'

/**
 * One column, named by what it points at rather than by what it says.
 *
 * <p>No label travels with it. What a column is called is the question's own business and is worked
 * out wherever the table is drawn, so a question renamed after a selection was saved is printed
 * under its new name.
 */
export interface MemberTableColumn {
    kind: MemberTableColumnKind
    key?: string | null
    fieldId?: number | null
}

/**
 * What a drawn column holds. Dates and birth dates arrive as `dd.mm.yyyy` from the station's own
 * questions and as ISO days from an appointment's; a boolean as the station's word for it or as
 * `true` and `false`.
 */
export const MemberTableCellTypes = {
    TEXT: 'TEXT',
    NUMBER: 'NUMBER',
    DATE: 'DATE',
    BIRTH_DATE: 'BIRTH_DATE',
    BOOLEAN: 'BOOLEAN',
    ENUM: 'ENUM',
} as const

export type MemberTableCellTypeName = (typeof MemberTableCellTypes)[keyof typeof MemberTableCellTypes]

/** A column as the reader may see it, which is what a picker offers and what a table prints. */
export interface MemberTableHeader {
    label: string
    kind: MemberTableColumnKind
    key: string | null
    fieldId: number | null
    type: MemberTableCellTypeName
}

/** One person's row, in the same order as the columns. */
export interface MemberTableRow {
    memberId: number
    values: string[]
}

/** A drawn table: only the columns this reader may see, and a row each. */
export interface MemberTable {
    columns: MemberTableHeader[]
    rows: MemberTableRow[]
}

/** A named set of columns a station saved. */
export interface MemberTablePreset {
    id: number
    stationId: number
    name: string
    columns: MemberTableColumn[]
}

/** What may go on an appointment's table: what the station knows, and what the appointment asked. */
export interface RegistrationTableColumns {
    member: MemberTableHeader[]
    questions: {fieldId: number; label: string; type: MemberTableCellTypeName}[]
}

/** The columns this reader may put on a table of the register. */
export async function listColumns(): Promise<MemberTableHeader[]> {
    const res = await client.get<MemberTableHeader[]>('/member-table/columns')
    return res.data
}

/** The columns this reader may put on one appointment's table, the appointment's own included. */
export async function listRegistrationColumns(eventId: number): Promise<RegistrationTableColumns> {
    const res = await client.get<RegistrationTableColumns>(`/events/${eventId}/registration-table/columns`)
    return res.data
}

export async function listPresets(): Promise<MemberTablePreset[]> {
    const res = await client.get<MemberTablePreset[]>('/member-table/presets')
    return res.data
}

/** Saves a selection under a name, writing over one saved under that name before. */
export async function savePreset(name: string, columns: MemberTableColumn[]): Promise<MemberTablePreset> {
    const res = await client.put<MemberTablePreset>('/member-table/presets', {name, columns})
    return res.data
}

export async function deletePreset(id: number): Promise<void> {
    await client.delete(`/member-table/presets/${id}`)
}

/** The register drawn for the people the screen is already showing. */
export async function drawMemberTable(memberIds: number[], columns: MemberTableColumn[]): Promise<MemberTable> {
    const res = await client.post<MemberTable>('/member-table', {memberIds, columns})
    return res.data
}

/** Everybody standing on one appointment's list for one day. */
export async function drawRegistrationTable(
    eventId: number, date: string, columns: MemberTableColumn[],
): Promise<MemberTable> {
    const res = await client.post<MemberTable>(`/events/${eventId}/registration-table`, {date, columns})
    return res.data
}

/**
 * The same table as a file.
 *
 * <p>Asked for from the server rather than built here, so that what is written down and what was on
 * screen cannot disagree about which columns this reader may see.
 */
export async function exportMemberTable(
    memberIds: number[],
    columns: MemberTableColumn[],
    format: 'csv' | 'pdf',
    separator: ExportSeparator = 'semicolon',
): Promise<DocumentFile> {
    const res = await client.post(
        `/member-table/export.${format}`,
        {memberIds, columns},
        {responseType: 'blob', params: format === 'csv' ? {separator} : undefined},
    )
    return documentFrom(res, `Mitglieder.${format}`)
}

export async function exportRegistrationTable(
    eventId: number,
    date: string,
    columns: MemberTableColumn[],
    format: 'csv' | 'pdf',
    separator: ExportSeparator = 'semicolon',
): Promise<DocumentFile> {
    const res = await client.post(
        `/events/${eventId}/registration-table/export.${format}`,
        {date, columns},
        {responseType: 'blob', params: format === 'csv' ? {separator} : undefined},
    )
    return documentFrom(res, `Anmeldungen.${format}`)
}
