/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const BYTE_ORDER_MARK = 0xfeff

/**
 * Reads an uploaded CSV file as text, in the encoding it was actually written in.
 *
 * `File.text()` always decodes as UTF-8 and turns anything malformed into the replacement
 * character, which destroys the original byte before the file reaches the server. Spreadsheet
 * software on Windows writes CSV as Windows-1252, where an umlaut is a single byte that UTF-8
 * has no reading for, so every name carrying one arrives broken and cannot be recovered.
 *
 * Decoding strictly as UTF-8 first settles which of the two it is: well formed UTF-8 always
 * succeeds, and Windows-1252 text carrying any accented character always fails, because those
 * bytes are not a valid sequence. So the fallback is a decision rather than a guess, and plain
 * ASCII reads the same either way.
 */
export async function readCsvText(file: File): Promise<string> {
    const bytes = new Uint8Array(await file.arrayBuffer())
    return stripByteOrderMark(decode(bytes))
}

function decode(bytes: Uint8Array): string {
    try {
        return new TextDecoder('utf-8', {fatal: true}).decode(bytes)
    } catch {
        return new TextDecoder('windows-1252').decode(bytes)
    }
}

/**
 * Removes a leading byte order mark, which spreadsheet software writes in front of a UTF-8
 * export. Left in place it becomes part of the first header name, so every lookup of that
 * column misses and the import drops it.
 */
function stripByteOrderMark(text: string): string {
    return text.charCodeAt(0) === BYTE_ORDER_MARK ? text.slice(1) : text
}
