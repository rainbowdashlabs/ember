/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import versions from '@/federation_versions.json'
import type { FederationContract, FederationPartner } from '@/api/federation'

const versionMap: Record<string, string> = Object.assign(
    {},
    ...Object.values(versions as Record<string, Record<string, string>>),
)

export function resolveFederationVersion(hash: string): string {
    return versionMap[hash] ?? hash.substring(0, 8)
}

export type PartnerCompatibility = 'compatible' | 'unknown' | 'incompatible' | 'partial'

export function partnerCompatibility(
    local: FederationContract | null,
    partner: FederationPartner,
): PartnerCompatibility {
    if (!partner.remoteHost || !local) return 'compatible'
    if (!partner.federationContract) return 'unknown'
    if (partner.federationContract.core !== local.core) return 'incompatible'
    const mismatch = Object.keys(local.features).some(
        capability => partner.federationContract!.features[capability] !== local.features[capability],
    )
    return mismatch ? 'partial' : 'compatible'
}

export function featureCompatible(
    local: FederationContract | null,
    partner: FederationPartner,
    capability: string,
): boolean {
    switch (partnerCompatibility(local, partner)) {
        case 'compatible':
            return true
        case 'partial':
            return partner.federationContract!.features[capability] === local!.features[capability]
        default:
            return false
    }
}
