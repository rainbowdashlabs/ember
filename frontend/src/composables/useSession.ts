/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {session} from '@/api'
import {getItem} from '@/api/storage'
import type {SessionInfo} from '@/api/types'
import {StationPermission} from '@/api/types'
import {useTheme} from '@/composables/useTheme'

const sessionInfo = ref<SessionInfo | null>(null)
const loaded = ref(false)
const loadFailed = ref(false)
const sessionStationId = ref<string | null>(null)
let loadSeq = 0

function isTransientError(error: unknown): boolean {
    const status = (error as {response?: {status?: number}})?.response?.status
    return status == null || status >= 500
}

export function useSession() {
    async function load() {
        const seq = ++loadSeq
        const requestedStation = getItem('station_id')
        for (let attempt = 1; ; attempt++) {
            try {
                const info = await session.getSessionInfo()
                if (seq !== loadSeq) return
                sessionInfo.value = info
                useTheme().initFromSession(info?.theme)
                loadFailed.value = false
                break
            } catch (error) {
                if (seq !== loadSeq) return
                if (!isTransientError(error) || attempt >= 3) {
                    sessionInfo.value = null
                    loadFailed.value = true
                    break
                }
                await new Promise(resolve => setTimeout(resolve, 500 * attempt))
            }
        }
        sessionStationId.value = requestedStation
        loaded.value = true
    }

    function clear() {
        loadSeq++
        sessionInfo.value = null
        loaded.value = false
        loadFailed.value = false
        sessionStationId.value = null
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
            || hasPermission(StationPermission.STATION_GENERAL)
            || hasPermission(StationPermission.STATION_LOOK_AND_FEEL)
            || hasPermission(StationPermission.STATION_MAIL)
            || hasPermission(StationPermission.STATION_MODULES)
            || hasPermission(StationPermission.STATION_IMPORT_EXPORT)
            || hasPermission(StationPermission.STATION_STATISTICS)
    }

    function canManageMembers(): boolean {
        return hasPermission(StationPermission.MEMBER_MANAGER)
    }

    function hasAnyMemberPermission(): boolean {
        return hasPermission(StationPermission.MEMBER_MANAGER)
            || hasPermission(StationPermission.MEMBER_READ)
            || hasPermission(StationPermission.MEMBER_EDIT)
            || hasPermission(StationPermission.MEMBER_NOTES)
            || hasPermission(StationPermission.MEMBER_CHANGES)
            || hasPermission(StationPermission.MEMBER_MANAGE_GROUP)
            || hasPermission(StationPermission.MEMBER_MANAGE_TAGS)
            || hasPermission(StationPermission.MEMBER_FIELDS)
    }

    function canManageInventory(): boolean {
        return hasPermission(StationPermission.INVENTORY_MANAGER)
    }

    function canManageExchanges(): boolean {
        return hasPermission(StationPermission.INVENTORY_EXCHANGE)
    }

    function canManageAttendance(): boolean {
        return hasPermission(StationPermission.ATTENDANCE_MANAGER)
    }

    function hasAnyAttendancePermission(): boolean {
        return hasPermission(StationPermission.ATTENDANCE_MANAGER)
            || hasPermission(StationPermission.ATTENDANCE_READ)
            || hasPermission(StationPermission.ATTENDANCE_EDIT)
            || hasPermission(StationPermission.ATTENDANCE_CONFIGURE)
            || hasPermission(StationPermission.ATTENDANCE_EXPORT)
    }

    function canExportAttendance(): boolean {
        return hasPermission(StationPermission.ATTENDANCE_EXPORT)
    }

    function canManageEvents(): boolean {
        return hasPermission(StationPermission.EVENT_MANAGER)
    }

    function hasAnyEventPermission(): boolean {
        return hasPermission(StationPermission.EVENT_MANAGER)
            || hasPermission(StationPermission.EVENT_EDIT)
            || hasPermission(StationPermission.EVENT_REGISTRATION)
            || hasPermission(StationPermission.EVENT_MANAGE_TEMPLATE)
            || hasPermission(StationPermission.EVENT_MANAGE_CATEGORY)
            || hasPermission(StationPermission.EVENTS_FEDERATE)
    }

    function canManageNews(): boolean {
        return hasPermission(StationPermission.NEWS_MANAGER)
    }

    function hasAnyNewsPermission(): boolean {
        return hasPermission(StationPermission.NEWS_MANAGER)
            || hasPermission(StationPermission.NEWS_EDIT)
            || hasPermission(StationPermission.NEWS_FEDERATE)
    }

    function canManagePolls(): boolean {
        return hasPermission(StationPermission.POLL_MANAGER)
    }

    function hasAnyPollPermission(): boolean {
        return hasPermission(StationPermission.POLL_MANAGER)
            || hasPermission(StationPermission.POLL_CREATE)
            || hasPermission(StationPermission.POLL_VIEW_RESULTS)
    }

    function isGuardian(): boolean {
        return hasPermission(StationPermission.MEMBER_GUARDIAN)
    }

    function canManageLostAndFound(): boolean {
        return hasPermission(StationPermission.LOST_AND_FOUND_MANAGER)
    }

    function hasAnyLostAndFoundPermission(): boolean {
        return hasPermission(StationPermission.LOST_AND_FOUND_MANAGER)
            || hasPermission(StationPermission.LOST_AND_FOUND_CREATE)
            || hasPermission(StationPermission.LOST_AND_FOUND_MANAGE)
    }

    function canManageWaitlist(): boolean {
        return hasPermission(StationPermission.WAITLIST_MANAGER)
    }

    function hasAnyWaitlistPermission(): boolean {
        return hasPermission(StationPermission.WAITLIST_MANAGER)
            || hasPermission(StationPermission.WAITLIST_READ)
            || hasPermission(StationPermission.WAITLIST_ADD)
            || hasPermission(StationPermission.WAITLIST_EDIT)
    }

    function canManageQuiz(): boolean {
        return hasPermission(StationPermission.TEST_MANAGER)
    }

    function hasAnyQuizPermission(): boolean {
        return hasPermission(StationPermission.TEST_MANAGER)
            || hasPermission(StationPermission.TEST_CATALOG_VIEW)
            || hasPermission(StationPermission.TEST_CATALOG_EDIT)
            || hasPermission(StationPermission.TEST_CONFIGURE)
            || hasPermission(StationPermission.PROTOCOL_MANAGER)
            || hasPermission(StationPermission.PROTOCOL_TESTER)
            || hasPermission(StationPermission.PROTOCOL_CREATE)
            || hasPermission(StationPermission.PROTOCOL_CONFIGURE)
    }

    function canManageKnowledge(): boolean {
        return hasPermission(StationPermission.KNOWLEDGE_MANAGER)
    }

    function canEditKnowledge(): boolean {
        return hasPermission(StationPermission.KNOWLEDGE_EDIT)
    }

    function hasAnyKnowledgePermission(): boolean {
        return hasPermission(StationPermission.KNOWLEDGE_MANAGER)
            || hasPermission(StationPermission.KNOWLEDGE_EDIT)
            || hasPermission(StationPermission.KNOWLEDGE_FEDERATE)
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
        loadFailed: readonly(loadFailed),
        sessionStationId: readonly(sessionStationId),
        load,
        clear,
        hasPermission,
        userType,
        isAdmin,
        isManager,
        canManageMembers,
        hasAnyMemberPermission,
        canManageInventory,
        canManageExchanges,
        canManageAttendance,
        hasAnyAttendancePermission,
        canExportAttendance,
        canManageEvents,
        hasAnyEventPermission,
        canManageNews,
        hasAnyNewsPermission,
        canManagePolls,
        hasAnyPollPermission,
        isGuardian,
        canManageLostAndFound,
        hasAnyLostAndFoundPermission,
        canManageWaitlist,
        hasAnyWaitlistPermission,
        canManageQuiz,
        hasAnyQuizPermission,
        canManageKnowledge,
        canEditKnowledge,
        hasAnyKnowledgePermission,
        canManageBoards,
        canManageFederation,
        canManageProtocol,
        canTestProtocol,
        isModuleEnabled,
        fullName,
        isKbPublic,
    }
}
