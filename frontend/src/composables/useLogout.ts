/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useRouter} from 'vue-router'
import {auth} from '@/api'
import {hasSessionCookie} from '@/api/sessionCookie'
import {useCluster} from '@/composables/useCluster'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import {useTheme} from '@/composables/useTheme'
import {forgetLandingMemory} from '@/util/landingMemoryState'
import {unsubscribeBrowser} from '@/util/pushSupport'

/**
 * Returns a single `logout` function that calls the backend revoke, wipes session
 * state from the browser, resets the theme to the instance defaults, and pushes
 * the user to the login route, whether or not the revoke succeeded.
 *
 * <p>The stations and associations an account may act for are held once for the whole
 * application and outlive the session, so they are cleared too; otherwise the next person
 * to sign in on this browser would be offered those of the one before them.
 *
 * <p>The browser also lets go of its push subscription first, so a shared device stops showing
 * the last person's notifications even before the server has forgotten the device with the sign-in.
 */
export function useLogout() {
  const router = useRouter()
  const {clear} = useSession()
  const {clear: clearStations} = useStations()
  const {clear: clearClusters} = useCluster()

  async function logout() {
    await unsubscribeBrowser().catch(() => {})
    if (hasSessionCookie()) {
      await auth.logout().catch(() => {})
    }
    clear()
    clearStations()
    clearClusters()
    forgetLandingMemory()
    useTheme().resetToInstanceDefaults()
    await router.push({name: 'login'})
  }

  return {logout}
}
