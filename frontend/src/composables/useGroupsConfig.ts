/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, inject, provide, ref, type InjectionKey, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {apiErrorMessage} from '@/util/apiError'
import type {MemberGroup, MemberIdentity, PermissionGrant} from '@/api/types'

/**
 * A person who can be put into a group or given a tag, which is all those panels ever read of one.
 *
 * <p>A station's groups and tags hold station members, who belong to a station and have an account
 * behind them. An association's groups hold the handful of people who run it, who are neither.
 * Naming what the panels actually use lets both pass through without either pretending to be the
 * other.
 */
export interface AssignableMember {
    id: number
    name?: string | null
    email?: string | null
    identity?: MemberIdentity | null
}

/**
 * A group's members and what it grants, as the panels want them.
 *
 * <p>The two owners keep permissions in different currencies. A station's are rows and are identified
 * by a number. An association's are names, all the way through its API. The picker draws numbers, so
 * an association's port numbers its own names on the way out and reads them back on the way in, and
 * the numbers never leave the port.
 */
export interface GroupDetailShape {
    members: AssignableMember[]
    /** The grants this group holds, in the numbered form the shared picker speaks. Absent where a group grants nothing. */
    roles?: PermissionGrant[]
}

/**
 * Where a set of groups lives, and what its owner may do with one.
 *
 * <p>A station groups the people who belong to it, gives a group a colour and an order, and can turn
 * one into a tag. An association groups the handful of people who run it and does none of those
 * things, and files its stations into groups that grant nothing at all. The panels are the same
 * panels; the difference sits here.
 *
 * <p>The three permission methods are optional, because a port whose groups grant nothing has
 * nothing to answer with.
 */
export interface GroupsPort {
    listGroups(): Promise<MemberGroup[]>
    listCandidates(): Promise<AssignableMember[]>
    listAllRoles?: () => Promise<PermissionGrant[]>
    getDetail(groupId: number): Promise<GroupDetailShape>
    createGroup(patch: {name: string; color: string | null; position: number}): Promise<unknown>
    updateGroup(groupId: number, patch: {name: string; color: string | null; position: number}): Promise<unknown>
    deleteGroup(groupId: number): Promise<unknown>
    setMembers(groupId: number, memberIds: number[]): Promise<unknown>
    setRoles?: (groupId: number, roleIds: number[]) => Promise<PermissionGrant[]>
    convertToTag?: (groupId: number) => Promise<unknown>
}

/**
 * What the panels below may draw, which follows from who owns the groups. Injected rather than handed
 * down through panels that have no use for it themselves.
 */
export interface GroupsCapabilities {
    /** A station's group carries a colour and a position; an association's carries neither. */
    hasColour: boolean
    /** Only a station can turn a group into a tag, because only a station has tags. */
    canConvertToTag: boolean
    /** A group of people grants permissions. A group of stations grants nothing. */
    hasPermissions: boolean
    /** What the group holds, which is what the assignment panel calls the things it lists. */
    holds: 'members' | 'stations'
}

const GROUPS_CAPABILITIES: InjectionKey<GroupsCapabilities> = Symbol('groupsCapabilities')

const STATION_CAPABILITIES: GroupsCapabilities = {
    hasColour: true,
    canConvertToTag: true,
    hasPermissions: true,
    holds: 'members',
}

export function useGroupsCapabilities(): GroupsCapabilities {
    return inject(GROUPS_CAPABILITIES, STATION_CAPABILITIES)
}

/**
 * The group screen, without its markup: the list, which one is open, its members and its grants, and
 * every write either panel can ask for.
 *
 * @param port         where the groups live and what may be done with one
 * @param capabilities what the panels may draw
 */
export function useGroupsConfig(port: GroupsPort, capabilities: GroupsCapabilities) {
    const {t} = useI18n()

    provide(GROUPS_CAPABILITIES, capabilities)

    const groups = ref<MemberGroup[]>([])
    const allMembers = ref<AssignableMember[]>([])
    const allRoles = ref<PermissionGrant[]>([])

    const selectedGroup = ref<MemberGroup | null>(null)
    const groupMembers = ref<AssignableMember[]>([])
    const groupRoles = ref<PermissionGrant[]>([])
    const groupLoading = ref(false)

    const showGroupModal = ref(false)
    const editingGroup = ref<MemberGroup | null>(null)
    const groupName = ref('')
    const groupColor = ref('')
    const groupPosition = ref(0)

    const {loading, error} = useAsyncLoader(async () => {
        const [g, m, r] = await Promise.all([
            port.listGroups(),
            port.listCandidates(),
            capabilities.hasPermissions && port.listAllRoles ? port.listAllRoles() : Promise.resolve([]),
        ])
        groups.value = g
        allMembers.value = m
        allRoles.value = r
    })

    const groupRoleIds = computed({
        get: () => new Set(groupRoles.value.map(r => r.id)),
        set: (newIds: Set<number>) => { void syncGroupRoles(newIds) },
    })

    async function selectGroup(group: MemberGroup) {
        selectedGroup.value = group
        groupLoading.value = true
        try {
            const detail = await port.getDetail(group.id)
            groupMembers.value = detail.members
            groupRoles.value = detail.roles ?? []
        } catch {
            error.value = t('common.error')
            groupMembers.value = []
            groupRoles.value = []
        } finally {
            groupLoading.value = false
        }
    }

    /** Forgets whichever group is open, for when it has just stopped existing. */
    async function refreshAfter(goneId: number) {
        if (selectedGroup.value?.id === goneId) {
            selectedGroup.value = null
            groupMembers.value = []
        }
        groups.value = await port.listGroups()
    }

    const {
        show: showDeleteModal,
        target: deleteTarget,
        requestDelete,
        confirm: confirmDelete,
    } = useConfirmDelete<MemberGroup>({
        onDelete: async g => { await port.deleteGroup(g.id) },
        onSuccess: deleted => refreshAfter(deleted.id),
        error,
    })

    function openCreateGroup() {
        editingGroup.value = null
        groupName.value = ''
        groupColor.value = ''
        groupPosition.value = 0
        showGroupModal.value = true
    }

    function openEditGroup(group: MemberGroup) {
        editingGroup.value = group
        groupName.value = group.name ?? ''
        groupColor.value = group.color ?? ''
        groupPosition.value = group.position ?? 0
        showGroupModal.value = true
    }

    const {running: groupSaving, error: groupSaveError, run: saveGroup} = useAsyncAction(async () => {
        error.value = ''
        const patch = {
            name: groupName.value,
            color: groupColor.value || null,
            position: groupPosition.value,
        }
        if (editingGroup.value) await port.updateGroup(editingGroup.value.id, patch)
        else await port.createGroup(patch)
        showGroupModal.value = false
        groups.value = await port.listGroups()
        const open = selectedGroup.value
        if (open && editingGroup.value?.id === open.id) {
            selectedGroup.value = groups.value.find(g => g.id === open.id) ?? null
        }
    }, {formatError: () => t('common.error')})

    async function syncGroupRoles(newIds: Set<number>) {
        const open = selectedGroup.value
        if (!open || !port.setRoles) return
        try {
            groupRoles.value = await port.setRoles(open.id, [...newIds])
        } catch (e: unknown) {
            error.value = apiErrorMessage(e) || t('common.error')
        }
    }

    async function setMembers(memberIds: number[]) {
        const open = selectedGroup.value
        if (!open) return
        await port.setMembers(open.id, memberIds)
    }

    return {
        groups: groups as Ref<MemberGroup[]>,
        allMembers,
        allRoles,
        selectedGroup,
        groupMembers,
        groupRoles,
        groupRoleIds,
        groupLoading,
        loading,
        error,
        showGroupModal,
        editingGroup,
        groupName,
        groupColor,
        groupPosition,
        groupSaving,
        groupSaveError,
        selectGroup,
        openCreateGroup,
        openEditGroup,
        saveGroup,
        showDeleteModal,
        deleteTarget,
        requestDelete,
        confirmDelete,
        setMembers,
        refreshAfter,
        canConvertToTag: computed(() => capabilities.canConvertToTag && !!port.convertToTag),
    }
}
