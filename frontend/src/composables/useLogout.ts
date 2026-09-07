/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useRouter} from 'vue-router'
import {auth} from '@/api'
import {getItem} from '@/api/storage'
import {useCluster} from '@/composables/useCluster'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import {useTheme} from '@/composables/useTheme'
import {forgetLandingMemory} from '@/util/landingMemoryState'

/**
 * Returns a single `logout` function that calls the backend revoke, wipes session
 * state from the browser, resets the theme to the instance defaults, and pushes
 * the user to the login route. Shared by the header account menu (`DashboardView`,
 * `AdminView`, `AccountView`) so the logout flow is defined in exactly one place.
 */
export function useLogout() {
  const router = useRouter()
  const {clear} = useSession()
  // The lists of what an account may act for outlive the session that fetched them: they are held once
  // for the whole application, so without this the next person to sign in on this browser is offered the
  // stations and associations of the one before them until the page is loaded afresh.
  const {clear: clearStations} = useStations()
  const {clear: clearClusters} = useCluster()

  async function logout() {
    const token = getItem('session_token')
    if (token) {
      try {
        await auth.logout({token})
      } catch {
        /* ignore */
      }
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
