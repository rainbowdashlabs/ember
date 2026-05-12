/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const CONSENT_KEY = 'storage_consent'

export type StorageConsent = 'accepted' | 'denied'

export function getConsent(): StorageConsent | null {
    const value = localStorage.getItem(CONSENT_KEY)
    if (value === 'accepted' || value === 'denied') {
        return value
    }
    return null
}

export function acceptStorage(): void {
    localStorage.setItem(CONSENT_KEY, 'accepted')
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
}
