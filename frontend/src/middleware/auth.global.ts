/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem} from '~/api/storage'
import {useConsentGuard} from '~/composables/useConsentGuard'

export default defineNuxtRouteMiddleware((to) => {
    if (!import.meta.client) return

    if (to.meta.public === true) return
    if (to.path === '/' || to.path === '/login' || to.path === '/2fa-verify') return
    if (to.path.startsWith('/helpcenter')) return

    const publicPaths = [
        '/forgot-password', '/set-password', '/reset-password',
        '/apply', '/waitlist', '/style', '/privacy', '/terms', '/imprint', '/patch-notes',
        '/discovery', '/public', '/waiting-list',
    ]
    if (publicPaths.some(p => to.path.startsWith(p))) return

    const token = getItem('session_token')
    if (!token) {
        return navigateTo({path: '/login', query: {redirect: to.fullPath}})
    }

    const {needsReconsent} = useConsentGuard()
    if (needsReconsent.value && to.path !== '/reconsent') {
        return navigateTo('/reconsent')
    }

    const lastActivity = localStorage.getItem('ember_last_activity')
    const now = Date.now()
    localStorage.setItem('ember_last_activity', String(now))
    if (lastActivity && now - Number(lastActivity) > 3600000 && to.path !== '/station/requirements') {
        return navigateTo({path: '/station/requirements', query: {redirect: to.fullPath}})
    }
})
