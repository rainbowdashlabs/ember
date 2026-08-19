/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** The answers the installer understands, by the name it reads them under. */
export interface InstallOptions {
    [key: string]: string
}

export interface InstallPreset {
    code: string
    validForHours: number
}

/**
 * Keeps a set of answers so the installer can fetch them with a short code.
 *
 * Nothing is stored beyond what the installer knows what to do with, and it lasts hours rather than
 * forever: a preset is worth nothing once the installation it was made for has run.
 */
export async function createPreset(options: InstallOptions): Promise<InstallPreset> {
    const res = await client.post<InstallPreset>('/public/install', {options})
    return res.data
}
