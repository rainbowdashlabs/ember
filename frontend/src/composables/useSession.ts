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
        return hasRole(Roles.MEMBER_MANAGEMENT)
    }

    function canManageInventory(): boolean {
        return hasRole(Roles.INVENTORY_MANAGEMENT)
    }

    function canManageAttendance(): boolean {
        return hasRole(Roles.ATTENDENCE_MANAGEMENT)
    }

    function canExportAttendance(): boolean {
        return hasRole(Roles.ATTENDENCE_EXPORT_MANAGER)
    }

    function canManageEvents(): boolean {
        return hasRole(Roles.EVENT_MANAGEMENT)
    }

    function canManageNews(): boolean {
        return hasRole(Roles.NEWS_MANAGEMENT)
    }

    function canManagePolls(): boolean {
        return hasRole(Roles.POLL_MANAGEMENT)
    }

    function isGuardian(): boolean {
        return hasRole(Roles.GUARDIAN)
    }

    function canManageLostAndFound(): boolean {
        return hasRole(Roles.LOST_AND_FOUND_MANAGEMENT)
    }

    function canManageWaitlist(): boolean {
        return hasRole(Roles.WAITLIST_MANAGEMENT)
    }

    function canManageQuiz(): boolean {
        return hasRole(Roles.QUIZ_MANAGEMENT)
    }

    function canManageKnowledge(): boolean {
        return hasRole(Roles.KNOWLEDGE_MANAGEMENT)
    }

    function canManageFederation(): boolean {
        return hasRole(Roles.FEDERATION_MANAGEMENT)
    }

    function canManageProtocol(): boolean {
        return hasRole(Roles.PROTOCOL_MANAGEMENT)
    }

    function canTestProtocol(): boolean {
        return hasRole(Roles.PROTOCOL_TESTER)
    }

    function isModuleEnabled(module: string): boolean {
        return !(sessionInfo.value?.disabledModules?.includes(module) ?? false)
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
    }
}
