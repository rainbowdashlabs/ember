/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, computed } from 'vue'
import {parseFieldConfig} from '@/api/profileFields'
import {calculatedAnswer} from '@/util/profileFields'
import { profileFields, stationMembers } from '@/api'
import type {
  GroupEntry,
  MemberIdentity,
  MemberWithName,
  Permission,
  ProfileField,
  ProfileFieldAssignment,
  RichMember,
  TagEntry,
} from '@/api/generated/schema'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useI18n } from 'vue-i18n'
import { describeFailure } from '@/util/failure'

/**
 * What to call somebody in a list. Takes the little of a person this needs rather than a station
 * member, so the same ordering serves an association's own people, who belong to no station.
 */
export function memberDisplayName(m: {id: number; name?: string | null; email?: string | null}): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

/** What the name helpers read of a person: the whole name and, where the list carries them, its halves. */
interface NamedMember {
  name?: string | null
  firstName?: string
  lastName?: string
}

/**
 * The two halves of a name, taken as they are stored where the server sends them.
 *
 * <p>Splitting the whole at the first space is a guess, and it is wrong for anybody with two given
 * names or two surnames: "Millie Jo Harnack" reads as a surname of "Jo Harnack". It stays as the
 * fallback for the lists that do not carry the halves.
 */
export function getMemberFirstName(m: NamedMember): string {
  if (m.firstName !== undefined) return m.firstName
  return (m.name ?? '').split(' ')[0] ?? ''
}

export function getMemberLastName(m: NamedMember): string {
  if (m.lastName !== undefined) return m.lastName
  return (m.name ?? '').split(' ').slice(1).join(' ')
}

/**
 * One person as the member list reads them: what a station's own roll sends, and what an association's
 * search can say about somebody at one of its stations.
 */
export type RosterMember = Pick<RichMember,
    'id' | 'stationId' | 'accountId' | 'name' | 'firstName' | 'lastName' | 'email' | 'accountSetupPending'
    | 'setupMailExpiresAt' | 'mailReaches' | 'former' | 'roles' | 'groups' | 'tags' | 'profileValues'>
    & {userType: string; identity: MemberIdentity}

/**
 * Where a member list gets its people, its questions and its grants.
 *
 * <p>A station reads its own roll. An association reads across the stations it governs, through its
 * own endpoints and a page at a time. Both hand back the same three things, so the screen above does
 * not know which it is looking at.
 */
export interface MemberDataSource {
  /**
   * @return the people, the questions, who each question is put to, and the permissions in force
   */
  load(): Promise<{
    members: RosterMember[]
    fields: ProfileField[]
    assignments: ProfileFieldAssignment[]
    roles: Permission[]
  }>
  /** Who manages this person, fetched when a row is opened. Absent where nobody does. */
  loadManagers?(memberId: number): Promise<MemberWithName[]>
}

/** The station's own roll, which is what this screen has always shown. */
export const STATION_MEMBER_SOURCE: MemberDataSource = {
  load: async () => {
    const [members, fields, assignments, roles] = await Promise.all([
      stationMembers.listRichMembers(),
      profileFields.listFields(),
      profileFields.listAssignments(),
      stationMembers.listAllPermissions(),
    ])
    return {members, fields, assignments, roles}
  },
  loadManagers: (memberId) => stationMembers.getManagers(memberId),
}

export function useMemberData(source: MemberDataSource = STATION_MEMBER_SOURCE) {
  const members = ref<RosterMember[]>([])
  const fields = ref<ProfileField[]>([])
  const assignments = ref<ProfileFieldAssignment[]>([])
  const allGroups = ref<GroupEntry[]>([])
  const allTags = ref<TagEntry[]>([])
  const allRoles = ref<Permission[]>([])
  const memberValues = ref<Map<number, Map<number, string>>>(new Map())
  const memberRolesMap = ref<Map<number, string[]>>(new Map())
  const memberGroupsMap = ref<Map<number, string[]>>(new Map())
  const memberTagsMap = ref<Map<number, string[]>>(new Map())
  const memberManagers = ref<Map<number, MemberWithName[]>>(new Map())
  const expandedId = ref<number | null>(null)

  const overviewFields = computed(() => fields.value.filter(f => parseFieldConfig(f.config).overview))

  function getRawFieldValue(memberId: number, fieldId: number): unknown {
    const vals = memberValues.value.get(memberId)
    if (!vals) return ''
    const raw = vals.get(fieldId) ?? ''
    try { return JSON.parse(raw) } catch { return raw }
  }

  function getFieldValue(memberId: number, fieldId: number): unknown {
    const field = fields.value.find(f => f.id === fieldId)
    const calculated = field && calculatedAnswer(field, fields.value, id => getRawFieldValue(memberId, id))
    return calculated ?? getRawFieldValue(memberId, fieldId)
  }

  function getFieldValueAsString(memberId: number, fieldId: number): string {
    const val = getFieldValue(memberId, fieldId)
    if (val == null) return ''
    return String(val)
  }

  function getMemberType(memberId: number): string | null {
    const member = members.value.find(m => m.id === memberId)
    return member?.userType ?? null
  }

  function getMemberGroups(memberId: number): string[] {
    return memberGroupsMap.value.get(memberId) ?? []
  }

  function getMemberTags(memberId: number): string[] {
    return memberTagsMap.value.get(memberId) ?? []
  }

  const {t} = useI18n()

  const {loading, error, failure, reload} = useAsyncLoader(async () => {
    const {members: richMembers, fields: allFields, assignments: allAssignments, roles} = await source.load()
    fields.value = allFields
    assignments.value = allAssignments
    allRoles.value = roles

    const valMap = new Map<number, Map<number, string>>()
    const rolesMap = new Map<number, string[]>()
    const groupsMap = new Map<number, string[]>()
    const tagsMap = new Map<number, string[]>()
    const groupSet = new Map<number, GroupEntry>()
    const tagSet = new Map<number, TagEntry>()

    for (const rm of richMembers) {
      rolesMap.set(rm.id, rm.roles)

      const fieldMap = new Map<number, string>()
      for (const [key, val] of Object.entries(rm.profileValues)) {
        fieldMap.set(Number(key), val != null ? String(val) : '')
      }
      valMap.set(rm.id, fieldMap)

      groupsMap.set(rm.id, rm.groups.map(g => g.name))
      for (const group of rm.groups) {
        if (!groupSet.has(group.id)) groupSet.set(group.id, group)
      }

      tagsMap.set(rm.id, rm.tags.map(t => t.name))
      for (const tag of rm.tags) {
        if (!tagSet.has(tag.id)) tagSet.set(tag.id, tag)
      }
    }

    members.value = richMembers
    memberValues.value = valMap
    memberRolesMap.value = rolesMap
    memberGroupsMap.value = groupsMap
    memberTagsMap.value = tagsMap
    allGroups.value = Array.from(groupSet.values())
    allTags.value = Array.from(tagSet.values())
  })

  /**
   * Opens a row and fetches who manages that person.
   *
   * <p>A failure here used to be swallowed, and the open row then read as though nobody managed them,
   * which for a young member is the opposite of the truth. It is said out loud instead.
   */
  async function toggleExpand(member: RosterMember) {
    if (expandedId.value === member.id) { expandedId.value = null; return }
    expandedId.value = member.id
    if (source.loadManagers && !memberManagers.value.has(member.id)) {
      try {
        const managers = await source.loadManagers(member.id)
        memberManagers.value = new Map([...memberManagers.value, [member.id, managers]])
      } catch (e) {
        failure.value = {...describeFailure(e, t), message: t('membersList.managersUnreadable')}
      }
    }
  }

  return {
    members,
    fields,
    assignments,
    allGroups,
    allTags,
    allRoles,
    memberValues,
    memberRolesMap,
    memberGroupsMap,
    memberTagsMap,
    memberManagers,
    loading,
    error,
    failure,
    expandedId,
    overviewFields,
    getFieldValue,
    getFieldValueAsString,
    getMemberType,
    getMemberGroups,
    getMemberTags,
    reload,
    toggleExpand,
  }
}
