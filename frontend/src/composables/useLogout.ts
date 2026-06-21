/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useRouter} from 'vue-router'
import {auth} from '@/api'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'
import {useTheme} from '@/composables/useTheme'

/**
 * Returns a single `logout` function that calls the backend revoke, wipes session
 * state from the browser, resets the theme to the instance defaults, and pushes
 * the user to the login route. Shared by the header account menu (`DashboardView`,
 * `AdminView`, `AccountView`) so the logout flow is defined in exactly one place.
 */
export function useLogout() {
  const router = useRouter()
  const {clear} = useSession()

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
    useTheme().resetToInstanceDefaults()
    await router.push({name: 'login'})
  }

  return {logout}
}
