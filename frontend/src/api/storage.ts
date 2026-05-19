/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const CONSENT_KEY = 'storage_consent'
const CONSENT_VERSION_KEY = 'consent_version'
const PRIVACY_VERSION_KEY = 'privacy_version'
const TOS_VERSION_KEY = 'tos_version'

export type StorageConsent = 'accepted' | 'denied'

export interface StoredLegalVersions {
    consent?: string | null
    privacy?: string | null
    tos?: string | null
}

export function getConsent(): StorageConsent | null {
    const value = localStorage.getItem(CONSENT_KEY)
    if (value === 'accepted' || value === 'denied') {
        return value
    }
    return null
}

export function acceptStorage(versions?: StoredLegalVersions): void {
    localStorage.setItem(CONSENT_KEY, 'accepted')
    if (versions?.consent) localStorage.setItem(CONSENT_VERSION_KEY, versions.consent)
    if (versions?.privacy) localStorage.setItem(PRIVACY_VERSION_KEY, versions.privacy)
    if (versions?.tos) localStorage.setItem(TOS_VERSION_KEY, versions.tos)
}

export function getStoredLegalVersions(): StoredLegalVersions {
    return {
        consent: localStorage.getItem(CONSENT_VERSION_KEY),
        privacy: localStorage.getItem(PRIVACY_VERSION_KEY),
        tos: localStorage.getItem(TOS_VERSION_KEY),
    }
}

export function getConsentVersion(): string | null {
    return localStorage.getItem(CONSENT_VERSION_KEY)
}

export function denyStorage(): void {
    clearStoredData()
    localStorage.setItem(CONSENT_KEY, 'denied')
}

export function isStorageAccepted(): boolean {
    return getConsent() === 'accepted'
}

export function isStorageDenied(): boolean {
    return getConsent() === 'denied'
}

export function setItem(key: string, value: string): void {
    if (!isStorageAccepted()) {
        return
    }
    localStorage.setItem(key, value)
}

export function getItem(key: string): string | null {
    return localStorage.getItem(key)
}

export function removeItem(key: string): void {
    localStorage.removeItem(key)
}

export function clearStoredData(): void {
    localStorage.removeItem('session_token')
    localStorage.removeItem('station_id')
    localStorage.removeItem(CONSENT_VERSION_KEY)
    localStorage.removeItem(PRIVACY_VERSION_KEY)
    localStorage.removeItem(TOS_VERSION_KEY)
}
