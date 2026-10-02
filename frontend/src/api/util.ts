/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {uploadFile} from './upload'
import type {CsvResponse} from './generated/schema'

export async function parseCsv(file: File, separator: string): Promise<CsvResponse> {
    return uploadFile<CsvResponse>('/util/csv/parse', {file, separator})
}
