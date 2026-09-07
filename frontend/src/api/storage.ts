/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const isClient = typeof window !== 'undefined'

const CONSENT_KEY = 'storage_consent'
const SCOPES_KEY = 'storage_scopes'
const CONSENT_VERSION_KEY = 'consent_version'
const PRIVACY_VERSION_KEY = 'privacy_version'
const TOS_VERSION_KEY = 'tos_version'

export type StorageConsent = 'accepted' | 'denied'

/**
 * How badly a stored value is needed. The technically required ones come with the consent to
 * storage itself; the other two are granted separately and can be withdrawn again.
 */
export const StorageNecessity = {
    REQUIRED: 'REQUIRED',
    FUNCTIONAL: 'FUNCTIONAL',
    COMFORT: 'COMFORT',
} as const

export type StorageNecessityName = (typeof StorageNecessity)[keyof typeof StorageNecessity]

/** The two levels a member decides on. Required storage is not among them. */
export const OPTIONAL_NECESSITIES: StorageNecessityName[] = [
    StorageNecessity.FUNCTIONAL,
    StorageNecessity.COMFORT,
]

/**
 * What every value the application stores is needed for.
 *
 * This is the runtime half of the disclosure the privacy policy carries; the published half is
 * generated from {@code browser_storage.json}. `lint-browser-storage` compares the two, so a key
 * that is missing here, missing there, or filed under a different necessity fails the build.
 */
const NECESSITY: Record<string, StorageNecessityName> = {
    storage_consent: StorageNecessity.REQUIRED,
    storage_scopes: StorageNecessity.REQUIRED,
    consent_version: StorageNecessity.REQUIRED,
    privacy_version: StorageNecessity.REQUIRED,
    tos_version: StorageNecessity.REQUIRED,
    session_token: StorageNecessity.REQUIRED,
    session_expires_at: StorageNecessity.REQUIRED,
    ember_last_activity: StorageNecessity.REQUIRED,
    station_id: StorageNecessity.REQUIRED,
    cluster_id: StorageNecessity.REQUIRED,
    ai_provider: StorageNecessity.FUNCTIONAL,
    ai_model: StorageNecessity.FUNCTIONAL,
    ai_api_key: StorageNecessity.FUNCTIONAL,
    instance_theme: StorageNecessity.FUNCTIONAL,
    instance_feel: StorageNecessity.FUNCTIONAL,
    theme_name: StorageNecessity.COMFORT,
    dark_mode: StorageNecessity.COMFORT,
    feel: StorageNecessity.COMFORT,
    theme: StorageNecessity.COMFORT,
    sidebar_collapsed: StorageNecessity.COMFORT,
    landing_area: StorageNecessity.COMFORT,
    onboarding_tour_completed: StorageNecessity.COMFORT,
    'eventsUpcoming.viewMode': StorageNecessity.COMFORT,
    'stationPages.filesPerPage': StorageNecessity.COMFORT,
    'ember.feedPreset': StorageNecessity.COMFORT,
    'inv-members-show-name': StorageNecessity.COMFORT,
    'inv-members-show-size': StorageNecessity.COMFORT,
    'inv-members-show-internal-id': StorageNecessity.COMFORT,
    'inv-members-visible-ids': StorageNecessity.COMFORT,
}

export interface StoredLegalVersions {
    consent?: string | null
    privacy?: string | null
    tos?: string | null
}

export function getConsent(): StorageConsent | null {
    if (!isClient) return null
    const value = localStorage.getItem(CONSENT_KEY)
    if (value === 'accepted' || value === 'denied') return value
    return null
}

/**
 * The optional levels the member has allowed. Required storage is always part of an accepted
 * consent and therefore not listed.
 *
 * A consent given before the levels existed covered the whole disclosure, so it counts as all of
 * them until the member says otherwise.
 */
export function getGrantedScopes(): StorageNecessityName[] {
    if (!isClient) return []
    const raw = localStorage.getItem(SCOPES_KEY)
    if (raw === null) return isStorageAccepted() ? [...OPTIONAL_NECESSITIES] : []
    return raw.split(',').filter((scope): scope is StorageNecessityName =>
        OPTIONAL_NECESSITIES.includes(scope as StorageNecessityName))
}

/**
 * Records the decision and drops whatever a withdrawn level had left behind.
 *
 * @param scopes the optional levels that stay allowed
 */
export function setGrantedScopes(scopes: StorageNecessityName[]): void {
    if (!isClient) return
    const granted = OPTIONAL_NECESSITIES.filter(scope => scopes.includes(scope))
    localStorage.setItem(SCOPES_KEY, granted.join(','))
    for (const scope of OPTIONAL_NECESSITIES) {
        if (granted.includes(scope)) continue
        for (const [key, necessity] of Object.entries(NECESSITY)) {
            if (necessity === scope) localStorage.removeItem(key)
        }
    }
}

export function acceptStorage(versions?: StoredLegalVersions, scopes?: StorageNecessityName[]): void {
    if (!isClient) return
    localStorage.setItem(CONSENT_KEY, 'accepted')
    if (scopes) setGrantedScopes(scopes)
    if (versions?.consent) localStorage.setItem(CONSENT_VERSION_KEY, versions.consent)
    if (versions?.privacy) localStorage.setItem(PRIVACY_VERSION_KEY, versions.privacy)
    if (versions?.tos) localStorage.setItem(TOS_VERSION_KEY, versions.tos)
}

export function getStoredLegalVersions(): StoredLegalVersions {
    if (!isClient) return {}
    return {
        consent: localStorage.getItem(CONSENT_VERSION_KEY),
        privacy: localStorage.getItem(PRIVACY_VERSION_KEY),
        tos: localStorage.getItem(TOS_VERSION_KEY),
    }
}

export function getConsentVersion(): string | null {
    if (!isClient) return null
    return localStorage.getItem(CONSENT_VERSION_KEY)
}

export function denyStorage(): void {
    if (!isClient) return
    clearStoredData()
    setGrantedScopes([])
    localStorage.setItem(CONSENT_KEY, 'denied')
}

export function isStorageAccepted(): boolean {
    return getConsent() === 'accepted'
}

export function isStorageDenied(): boolean {
    return getConsent() === 'denied'
}

/**
 * Whether a value may be written. Required values ride along with the consent itself, the others
 * need their level to be allowed. A key that is not declared is never written.
 */
export function isStorageAllowed(key: string): boolean {
    if (!isStorageAccepted()) return false
    const necessity = NECESSITY[key]
    if (necessity === undefined) return false
    if (necessity === StorageNecessity.REQUIRED) return true
    return getGrantedScopes().includes(necessity)
}

export function setItem(key: string, value: string): void {
    if (!isClient || !isStorageAllowed(key)) return
    localStorage.setItem(key, value)
}

export function getItem(key: string): string | null {
    if (!isClient) return null
    return localStorage.getItem(key)
}

export function removeItem(key: string): void {
    if (!isClient) return
    localStorage.removeItem(key)
}

export function clearStoredData(): void {
    if (!isClient) return
    localStorage.removeItem('session_token')
    localStorage.removeItem('station_id')
    localStorage.removeItem('cluster_id')
    localStorage.removeItem(CONSENT_VERSION_KEY)
    localStorage.removeItem(PRIVACY_VERSION_KEY)
    localStorage.removeItem(TOS_VERSION_KEY)
}
