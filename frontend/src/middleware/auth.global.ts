/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem} from '~/api/storage'
import {useConsentGuard} from '~/composables/useConsentGuard'
import {useStations} from '~/composables/useStations'

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

    if (to.path === '/station' || to.path.startsWith('/station/')) {
        const queryStation = typeof to.query.station === 'string' ? to.query.station : null
        if (queryStation && queryStation !== getItem('station_id')) {
            useStations().setActiveStation(queryStation)
        } else if (!queryStation && !getItem('station_id')) {
            return navigateTo({path: '/cross-station', query: {redirect: to.fullPath}})
        }
    }
})
