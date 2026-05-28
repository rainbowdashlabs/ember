/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import versions from '@/federation_versions.json'

const versionMap: Record<string, string> = versions

export function resolveFederationVersion(hash: string): string {
    return versionMap[hash] ?? hash.substring(0, 8)
}
