/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {uploadFile} from './upload'

export interface ParsedCsv {
    headers: string[]
    rows: string[][]
}

export async function parseCsv(file: File, separator: string): Promise<ParsedCsv> {
    return uploadFile<ParsedCsv>('/util/csv/parse', {file, separator})
}
