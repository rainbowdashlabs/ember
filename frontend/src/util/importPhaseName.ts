/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const KEEP_ABBREVIATIONS: Record<string, string> = {
    kb: 'KB',
    url: 'URL',
    id: 'ID',
    uid: 'UID',
    fk: 'FK',
    pk: 'PK',
    api: 'API',
    json: 'JSON',
    sql: 'SQL',
}

function titleCaseWord(word: string): string {
    if (!word) return word
    const lower = word.toLowerCase()
    if (KEEP_ABBREVIATIONS[lower]) return KEEP_ABBREVIATIONS[lower]
    return lower.charAt(0).toUpperCase() + lower.slice(1)
}

function humanizeSnake(token: string): string {
    return token
        .split('_')
        .filter((s) => s.length > 0)
        .map(titleCaseWord)
        .join(' ')
}

/**
 * Renders a backend phase id as a label for the import-progress checklist. Phase ids fall
 * into three shapes:
 *   - `<snake_case_table>` - a tracked table (e.g. `federation_lending_request`)
 *   - `storage_backend`   - the source-backend handshake step
 *   - `files_<snake_case_category>` - one file-copy phase per movable storage category
 *   - `account_avatars`   - avatar carry-over for newly-created accounts
 *
 * Tables and the named steps render their snake_case as Title Case. File category phases
 * render as `Files: <Category>` so the checklist groups them visually.
 */
export function importPhaseName(phaseId: string): string {
    if (!phaseId) return ''
    if (phaseId.startsWith('files_')) {
        return `Files: ${humanizeSnake(phaseId.slice('files_'.length))}`
    }
    return humanizeSnake(phaseId)
}
