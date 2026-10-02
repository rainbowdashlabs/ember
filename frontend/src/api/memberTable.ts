/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {ExportSeparator} from '@/util/exportFormat'
import type {
    MemberTable,
    MemberTableColumn,
    MemberTableHeader,
    MemberTablePreset,
    TableColumnsResponse,
} from './generated/schema'

/**
 * One column as a screen asks for it, named by what it points at rather than by what it says.
 *
 * <p>A saved column comes back with a well-formed flag as well; a request names only the three
 * parts that point at the column.
 */
export type ColumnChoice = Pick<MemberTableColumn, 'kind' | 'key' | 'fieldId'>

/** The columns this reader may put on a table of the register. */
export async function listColumns(): Promise<MemberTableHeader[]> {
    const res = await client.get<MemberTableHeader[]>('/member-table/columns')
    return res.data
}

/** The columns this reader may put on one appointment's table, the appointment's own included. */
export async function listRegistrationColumns(eventId: number): Promise<TableColumnsResponse> {
    const res = await client.get<TableColumnsResponse>(`/events/${eventId}/registration-table/columns`)
    return res.data
}

export async function listPresets(): Promise<MemberTablePreset[]> {
    const res = await client.get<MemberTablePreset[]>('/member-table/presets')
    return res.data
}

/** Saves a selection under a name, writing over one saved under that name before. */
export async function savePreset(name: string, columns: ColumnChoice[]): Promise<MemberTablePreset> {
    const res = await client.put<MemberTablePreset>('/member-table/presets', {name, columns})
    return res.data
}

export async function deletePreset(id: number): Promise<void> {
    await client.delete(`/member-table/presets/${id}`)
}

/** The register drawn for the people the screen is already showing. */
export async function drawMemberTable(memberIds: number[], columns: ColumnChoice[]): Promise<MemberTable> {
    const res = await client.post<MemberTable>('/member-table', {memberIds, columns})
    return res.data
}

/** Everybody standing on one appointment's list for one day. */
export async function drawRegistrationTable(
    eventId: number, date: string, columns: ColumnChoice[],
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
    columns: ColumnChoice[],
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
    columns: ColumnChoice[],
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
