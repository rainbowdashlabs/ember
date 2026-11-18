/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {watch} from 'vue'
import {useSession} from '@/composables/useSession'
import {PUSH_SUBSCRIPTION_CHANGED, renewPushRegistration} from '@/composables/pushRenewal'

/**
 * Keeps this browser registered for push with whoever is signed in: once per sign-in, and again whenever the
 * service worker reports that the browser moved its push address. Without it the registration would only come
 * back once somebody opened the notification settings.
 */
export default defineNuxtPlugin(() => {
    const {sessionInfo} = useSession()
    watch(() => sessionInfo.value?.account?.id ?? null, account => {
        if (account !== null) void renewPushRegistration()
    }, {immediate: true})
    navigator.serviceWorker?.addEventListener('message', event => {
        if (event.data?.type === PUSH_SUBSCRIPTION_CHANGED) void renewPushRegistration()
    })
})
