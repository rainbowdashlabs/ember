/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {InstallPresetResponse} from './generated/schema'

/** The answers the installer understands, by the name it reads them under. */
export type InstallOptions = Record<string, string>

/**
 * Keeps a set of answers so the installer can fetch them with a short code.
 *
 * Nothing is stored beyond what the installer knows what to do with, and it lasts hours rather than
 * forever: a preset is worth nothing once the installation it was made for has run.
 */
export async function createPreset(options: InstallOptions): Promise<InstallPresetResponse> {
    const res = await client.post<InstallPresetResponse>('/public/install', {options})
    return res.data
}
