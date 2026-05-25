/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {session} from '@/api'
import type {SessionInfo} from '@/api/types'
import {Roles} from '@/api/types'
import {useTheme} from '@/composables/useTheme'

const sessionInfo = ref<SessionInfo | null>(null)
const loaded = ref(false)

export function useSession() {
    async function load() {
        try {
            sessionInfo.value = await session.getSessionInfo()
            useTheme().initFromSession(sessionInfo.value?.theme)
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
        return hasRole(Roles.ADMIN)
    }

    function isManager(): boolean {
        return hasRole(Roles.MANAGER)
    }

    function canManageMembers(): boolean {
        return hasRole(Roles.MEMBER_MANAGER)
    }

    function canManageInventory(): boolean {
        return hasRole(Roles.INVENTORY_MANAGER)
    }

    function canManageAttendance(): boolean {
        return hasRole(Roles.ATTENDANCE_MANAGER)
    }

    function canExportAttendance(): boolean {
        return hasRole(Roles.ATTENDANCE_EXPORT_MANAGER)
    }

    function canManageEvents(): boolean {
        return hasRole(Roles.EVENT_MANAGER)
    }

    function canManageNews(): boolean {
        return hasRole(Roles.NEWS_MANAGER)
    }

    function canManagePolls(): boolean {
        return hasRole(Roles.POLL_MANAGER)
    }

    function isGuardian(): boolean {
        return hasRole(Roles.GUARDIAN)
    }

    function canManageLostAndFound(): boolean {
        return hasRole(Roles.LOST_AND_FOUND_MANAGER)
    }

    function canManageWaitlist(): boolean {
        return hasRole(Roles.WAITLIST_MANAGER)
    }

    function canManageQuiz(): boolean {
        return hasRole(Roles.QUIZ_MANAGER)
    }

    function canManageKnowledge(): boolean {
        return hasRole(Roles.KNOWLEDGE_MANAGER)
    }

    function canManageProtocol(): boolean {
        return hasRole(Roles.PROTOCOL_MANAGER)
    }

    function canTestProtocol(): boolean {
        return hasRole(Roles.PROTOCOL_TESTER)
    }

    function isModuleEnabled(module: string): boolean {
        return !(sessionInfo.value?.disabledModules?.includes(module) ?? false)
    }

    function canManageFederation(): boolean {
        return hasRole(Roles.FEDERATION_MANAGER)
    }

    function fullName(): string {
        const account = sessionInfo.value?.account
        if (!account) return ''
        return [account.firstName, account.lastName].filter(Boolean).join(' ')
    }

    function isKbPublic(): boolean {
        return sessionInfo.value?.publicKbMode != null && sessionInfo.value.publicKbMode !== 'OFF'
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
        canManagePolls,
        isGuardian,
        canManageLostAndFound,
        canManageWaitlist,
        canManageQuiz,
        canManageKnowledge,
        canManageFederation,
        canManageProtocol,
        canTestProtocol,
        isModuleEnabled,
        fullName,
        isKbPublic,
    }
}
