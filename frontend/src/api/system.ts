/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface UpdateStatus {
    /** The version this instance runs. */
    currentVersion: string
    /** The newest release found, null where no check has succeeded. */
    latestVersion: string | null
    /** Whether the newest release is ahead of the running one. */
    updateAvailable: boolean
}

export async function getUpdateStatus(): Promise<UpdateStatus> {
    const res = await client.get<UpdateStatus>('/system/update')
    return res.data
}

/** What one version of this instance brought. */
export interface ChangelogEntry {
    /** The version it belongs to, as the numbers alone. */
    version: string
    /** What it brought, as markdown. */
    body: string
    /** When it was tagged, or null where this build knows no tag of it. */
    releasedAt?: string | null
    /** Where its changes can be read against the release before it, or null where there is no such pair. */
    compareUrl?: string | null
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
