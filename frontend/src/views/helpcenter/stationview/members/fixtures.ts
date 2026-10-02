/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
  FieldType,
  StationUserType,
  type EnrichedMemberChangeSummary,
  type MemberGroupSet,
  type MemberIdentity,
  type Permission,
  type UserTag,
} from '@/api/generated/schema'
import type {ChangeEntry} from '@/api/profileFieldChanges'
import type {AssignableMember} from '@/composables/useGroupsConfig'
import type {PermissionGrant} from '@/composables/usePermissionTree'
import {fromMember, type MemberOption} from '@/components/input/select/memberOption'
import type {GroupRow} from '@/util/groupRules'

/** Looks up a help text, so the demo names follow the help center's own sample people and groups. */
type Translate = (key: string) => string

const STATION_UID = '00000000-0000-4000-8000-000000000001'

/** A sample member of the station. */
function person(id: number, name: string): AssignableMember {
  return {id, name, userType: StationUserType.MEMBER}
}

/** The identity a change or a summary carries for a sample member. */
function identity(id: number, name: string): MemberIdentity {
  return {
    memberUid: `00000000-0000-4000-8000-00000000010${id}`,
    stationUid: STATION_UID,
    name,
    nameColor: null,
    displayTag: null,
    stationName: null,
  }
}

/** Two tags, the first one visible on the profile. */
export function demoTags(t: Translate): UserTag[] {
  return [
    {id: 1, stationId: STATION_UID, position: 0, name: t('helpCenter.sample.groups.firstAiders'), color: '#ec2929', visible: true},
    {id: 2, stationId: STATION_UID, position: 1, name: t('helpCenter.sample.groups.drivers'), color: '#3694FF', visible: false},
  ]
}

/** Who carries the selected tag. */
export function demoTagMembers(t: Translate): AssignableMember[] {
  return [person(1, t('helpCenter.sample.people.maxMustermann'))]
}

/** Who could still be given a tag or put into a group. */
export function demoCandidates(t: Translate): MemberOption[] {
  return [person(3, t('helpCenter.sample.people.lisaWeber')), person(4, t('helpCenter.sample.people.annaSchmidt'))].map(fromMember)
}

/** One set of groups that a member belongs to only one of at a time. */
export function demoGroupSets(t: Translate): MemberGroupSet[] {
  return [{id: 1, stationId: STATION_UID, name: t('helpCenter.sample.groups.levels')}]
}

/** Two groups of the set, the first one bound to members. */
export function demoGroups(t: Translate): GroupRow[] {
  return [
    {id: 1, name: t('helpCenter.sample.groups.beginners'), color: '#3694FF', position: 0, groupSetId: 1, userTypes: [StationUserType.MEMBER]},
    {id: 2, name: t('helpCenter.sample.groups.advancedPlural'), color: '#00C507', position: 1, groupSetId: 1, userTypes: []},
  ]
}

/** Who is in the selected group. */
export function demoGroupMembers(t: Translate): AssignableMember[] {
  return [person(1, t('helpCenter.sample.people.maxMustermann')), person(2, t('helpCenter.sample.people.annaSchmidt'))]
}

/** Two permissions a group can grant. */
export const DEMO_PERMISSIONS: Permission[] = [
  {id: 1, permission: 'ATTENDANCE_MANAGER'},
  {id: 2, permission: 'INVENTORY_READ'},
]

/** The permissions the selected group grants. */
export const DEMO_GROUP_GRANTS: PermissionGrant[] = DEMO_PERMISSIONS.slice(0, 1)

/** One member with two changes waiting to be acknowledged. */
export function demoChangeSummary(t: Translate): EnrichedMemberChangeSummary {
  return {
    memberId: 1,
    memberName: t('helpCenter.sample.people.maxMustermann'),
    identity: identity(1, t('helpCenter.sample.people.maxMustermann')),
    latestChange: '2026-05-14T13:30:00Z',
    pendingCount: 2,
  }
}

/** The member's changes: a new phone number still open and a clothing size already acknowledged. */
export function demoChanges(t: Translate): ChangeEntry[] {
  const base = {
    memberId: 1,
    memberName: t('helpCenter.sample.people.maxMustermann'),
    memberIdentity: identity(1, t('helpCenter.sample.people.maxMustermann')),
    changedBy: 1,
    changedByName: t('helpCenter.sample.people.maxMustermann'),
    clusterFieldId: null,
    fieldType: FieldType.TEXT,
    requiresAcknowledgement: true,
  }
  return [
    {
      ...base,
      id: 1,
      fieldId: 1,
      fieldName: t('helpCenter.exampleFields.phone'),
      changedAt: '2026-05-14T13:30:00Z',
      oldValue: JSON.stringify('0170 1111111'),
      newValue: JSON.stringify('0170 2222222'),
      acknowledgements: [],
    },
    {
      ...base,
      id: 2,
      fieldId: 2,
      fieldName: t('helpCenter.exampleFields.clothingSize'),
      changedAt: '2026-05-13T08:00:00Z',
      oldValue: JSON.stringify('S'),
      newValue: JSON.stringify('M'),
      acknowledgements: [{
        id: 1,
        changeId: 2,
        acknowledgedBy: 4,
        acknowledgedByName: t('helpCenter.sample.people.annaSchmidt'),
        acknowledgedAt: '2026-05-13T12:15:00Z',
        comment: null,
      }],
    },
  ]
}
