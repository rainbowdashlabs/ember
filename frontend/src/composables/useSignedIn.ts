/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, ref} from 'vue'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'

/**
 * Whether this browser is carrying a session, which only the browser can answer.
 *
 * The server renders without any storage to read, so it knows neither that somebody is signed in
 * nor that nobody is. Rendering a login call to action on that ignorance puts it in front of
 * people who are already signed in, and it stays there until the session call comes back.
 * Undecided is its own state: `signedIn` and `anonymous` are both false until the browser has
 * looked, so a call site can show neither.
 */
export function useSignedIn() {
    const {sessionInfo, loaded, load} = useSession()
    const carriesSession = ref<boolean | null>(null)

    const signedIn = computed(() => loaded.value && !!sessionInfo.value?.account)
    const anonymous = computed(() =>
        carriesSession.value === false || (carriesSession.value === true && loaded.value && !sessionInfo.value?.account))

    onMounted(() => {
        const token = getItem('session_token')
        carriesSession.value = !!token
        if (token && !loaded.value) load()
    })

    return {signedIn, anonymous, carriesSession}
}
