/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export const RELAY_PROVIDER_NAMES: Record<string, string> = {
    RAPIDMAIL: 'RapidMail',
    TWILIO: 'Twilio',
    SWEEGO: 'Sweego',
    BREVO: 'Brevo',
}

/**
 * Providers reached at an address the sender has to supply.
 *
 * Most relays answer at one well-known name for everybody. Sweego does not: every account gets its
 * own relay host and port from its credentials page, so its address can no more be assumed than
 * that of somebody's own server.
 */
const PROVIDERS_WITH_SERVER = ['SMTP', 'SWEEGO']

export function needsServerAddress(provider: string | undefined): boolean {
    return PROVIDERS_WITH_SERVER.includes(provider ?? '')
}

/**
 * What a provider lets a station send for nothing.
 *
 * Kept in one place because these are somebody else's terms and they change: `checked` says when
 * the figure was last confirmed, and `pricingUrl` is where a reader settles it for themselves. A
 * provider without `perDay` has no free allowance worth naming, and `noteKey` explains why.
 */
export interface ProviderFreeTier {
    perDay?: number
    noteKey?: string
    pricingUrl?: string
    checked?: string
}

/**
 * Listed in the order the provider picker offers them, so the tiles and the field below them read
 * as the same list. A station's own server is among them: what it may send is nobody else's terms,
 * but leaving it out would make the row look like it had a hole in it.
 */
export const PROVIDER_FREE_TIER: Record<string, ProviderFreeTier> = {
    SMTP: {noteKey: 'ownServer'},
    BREVO: {perDay: 300, pricingUrl: 'https://www.brevo.com/pricing/', checked: '2026-08'},
    SWEEGO: {perDay: 100, pricingUrl: 'https://www.sweego.io/pricing', checked: '2026-08'},
    TWILIO: {
        noteKey: 'trialOnly',
        pricingUrl: 'https://www.twilio.com/en-us/products/email-api/pricing',
        checked: '2026-08',
    },
    RAPIDMAIL: {noteKey: 'paidOnly', pricingUrl: 'https://www.rapidmail.de/preise', checked: '2026-08'},
}
