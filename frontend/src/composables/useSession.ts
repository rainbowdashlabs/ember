/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {session} from '@/api'
import type {SessionInfo} from '@/api/types'
import {StationPermission} from '@/api/types'
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

    function hasPermission(permission: string): boolean {
        return sessionInfo.value?.permissions?.includes(permission) ?? false
    }

    function userType(): string | undefined {
        return sessionInfo.value?.userType
    }

    function isAdmin(): boolean {
        return sessionInfo.value?.instanceUserType === 'ADMINISTRATOR'
    }

    function isManager(): boolean {
        return hasPermission(StationPermission.STATION_ADMINISTRATOR)
    }

    function canManageMembers(): boolean {
        return hasPermission(StationPermission.MEMBER_MANAGER)
    }

    function canManageInventory(): boolean {
        return hasPermission(StationPermission.INVENTORY_MANAGER)
    }

    function canManageAttendance(): boolean {
        return hasPermission(StationPermission.ATTENDANCE_MANAGER)
    }

    function canExportAttendance(): boolean {
        return hasPermission(StationPermission.ATTENDANCE_EXPORT)
    }

    function canManageEvents(): boolean {
        return hasPermission(StationPermission.EVENT_MANAGER)
    }

    function canManageNews(): boolean {
        return hasPermission(StationPermission.NEWS_MANAGER)
    }

    function canManagePolls(): boolean {
        return hasPermission(StationPermission.POLL_MANAGER)
    }

    function isGuardian(): boolean {
        return hasPermission(StationPermission.MEMBER_GUARDIAN)
    }

    function canManageLostAndFound(): boolean {
        return hasPermission(StationPermission.LOST_AND_FOUND_MANAGER)
    }

    function canManageWaitlist(): boolean {
        return hasPermission(StationPermission.WAITLIST_MANAGER)
    }

    function canManageQuiz(): boolean {
        return hasPermission(StationPermission.QUIZ_MANAGER)
    }

    function canManageKnowledge(): boolean {
        return hasPermission(StationPermission.KNOWLEDGE_MANAGER)
    }

    function canManageProtocol(): boolean {
        return hasPermission(StationPermission.PROTOCOL_MANAGER)
    }

    function canTestProtocol(): boolean {
        return hasPermission(StationPermission.PROTOCOL_TESTER)
    }

    function isModuleEnabled(module: string): boolean {
        return !(sessionInfo.value?.disabledModules?.includes(module) ?? false)
    }

    function canManageBoards(): boolean {
        return hasPermission(StationPermission.BOARD_MANAGER)
    }

    function canManageFederation(): boolean {
        return hasPermission(StationPermission.STATION_FEDERATION)
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
        hasPermission,
        userType,
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
        canManageBoards,
        canManageFederation,
        canManageProtocol,
        canTestProtocol,
        isModuleEnabled,
        fullName,
        isKbPublic,
    }
}
