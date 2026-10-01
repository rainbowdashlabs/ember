/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ChangelogEntry, UpdateStatus} from './generated/schema'

/** The version this instance runs, the newest release found, and whether that one is ahead. */
export async function getUpdateStatus(): Promise<UpdateStatus> {
    const res = await client.get<UpdateStatus>('/system/update')
    return res.data
}

/**
 * What every version brought, newest first, as the instance itself holds it.
 *
 * <p>Read from the instance rather than from GitHub, so an installation with no way out can still
 * say what it changed and nobody has to leave an address elsewhere to find out.
 */
export async function getChangelog(lang = 'de'): Promise<ChangelogEntry[]> {
    const res = await client.get<ChangelogEntry[]>('/public/changelog', {params: {lang}})
    return res.data
}
