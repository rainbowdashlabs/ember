/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getPushKey, registerPushDevice} from '@/api/push'
import {getItem, removeItem, setItem} from '@/api/storage'
import {currentBrowserSubscription, deviceLabel, pushState, subscribeBrowser} from '@/util/pushSupport'

/** What the service worker sends to open pages once the browser handed it a new push address. */
export const PUSH_SUBSCRIPTION_CHANGED = 'push-subscription-changed'

const PUSH_ENDPOINT_KEY = 'push_endpoint'

/** Remembers the push address this browser was registered under, so a renewal can bring it along once it moves. */
export function rememberPushEndpoint(endpoint: string) {
    setItem(PUSH_ENDPOINT_KEY, endpoint)
}

/** Forgets the remembered push address, once push was taken off this browser. */
export function forgetPushEndpoint() {
    removeItem(PUSH_ENDPOINT_KEY)
}

/**
 * Registers this browser again without asking, where it still has the permission.
 *
 * <p>A registration ends with the sign-in it was made in, and the browser may move its push address or drop its
 * subscription on its own; nobody should have to switch push on again after either. The page sends the address
 * it registered last, and subscribes anew where the browser dropped the subscription. The server takes the
 * renewal only for a browser this account allowed and did not remove since, so a browser somebody else allowed
 * does not start pushing to whoever signs in next. Nothing here is shown to the reader: a renewal that fails is
 * tried again on the next sign-in.
 *
 * @return whether the browser is registered now
 */
export async function renewPushRegistration(): Promise<boolean> {
    if (pushState() !== 'allowed') return false
    try {
        const previous = getItem(PUSH_ENDPOINT_KEY)
        let subscription = await currentBrowserSubscription()
        if (!subscription && previous) {
            const key = await getPushKey()
            if (key.enabled) subscription = await subscribeBrowser(key.publicKey)
        }
        if (!subscription) return false
        const moved = previous !== null && previous !== subscription.endpoint ? previous : null
        const device = await registerPushDevice({
            ...subscription,
            label: deviceLabel(),
            renewal: true,
            previousEndpoint: moved,
        })
        if (device) rememberPushEndpoint(subscription.endpoint)
        return device !== null
    } catch {
        return false
    }
}
