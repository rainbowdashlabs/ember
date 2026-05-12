/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {session} from '@/api'
import type {SessionInfo} from '@/api/types'

const sessionInfo = ref<SessionInfo | null>(null)
const loaded = ref(false)

export function useSession() {
    async function load() {
        try {
            sessionInfo.value = await session.getSessionInfo()
        } catch {
            sessionInfo.value = null
        }
        loaded.value = true
    }

    function clear() {
        sessionInfo.value = null
        loaded.value = false
    }

    function hasRole(role: string): boolean {
        return sessionInfo.value?.roles?.includes(role) ?? false
    }

    function isAdmin(): boolean {
        return hasRole('ADMIN')
    }

    function isManager(): boolean {
        return hasRole('MANAGER')
    }

    function canManageMembers(): boolean {
        return hasRole('MEMBER_MANAGEMENT')
    }

    function canManageInventory(): boolean {
        return hasRole('INVENTORY_MANAGEMENT')
    }

    function canManageAttendance(): boolean {
        return hasRole('ATTENDENCE_MANAGEMENT')
    }

    function canExportAttendance(): boolean {
        return hasRole('ATTENDENCE_EXPORT_MANAGER')
    }

    function canManageEvents(): boolean {
        return hasRole('EVENT_MANAGEMENT')
    }

    function canManageNews(): boolean {
        return hasRole('NEWS_MANAGEMENT')
    }

    function isMemberManager(): boolean {
        return hasRole('MEMBER_MANAGER')
    }

    function fullName(): string {
        const account = sessionInfo.value?.account
        if (!account) return ''
        return [account.firstName, account.lastName].filter(Boolean).join(' ')
    }

    return {
        sessionInfo: readonly(sessionInfo),
        loaded: readonly(loaded),
        load,
        clear,
        hasRole,
        isAdmin,
        isManager,
        canManageMembers,
        canManageInventory,
        canManageAttendance,
        canExportAttendance,
        canManageEvents,
        canManageNews,
        isMemberManager,
        fullName,
    }
}
