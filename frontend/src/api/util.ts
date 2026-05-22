/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface ParsedCsv {
    headers: string[]
    rows: string[][]
}

export async function parseCsv(file: File, separator: string): Promise<ParsedCsv> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('separator', separator)
    const res = await client.post<ParsedCsv>('/util/csv/parse', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    })
    return res.data
}
