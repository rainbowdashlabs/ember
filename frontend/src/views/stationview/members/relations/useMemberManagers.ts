/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProfileField } from '@/api/profileFields'
import { StationUserType, type StationMember } from '@/api/types'
import { members, profileFields, stationMembers } from '@/api'
import { memberDisplayName } from '../listview/useMemberData'
import { describeFailure, FailureKind, type Failure } from '@/util/failure'

/**
 * Owns the managers linked to the viewed member: their profile snapshots plus
 * linking an existing member, unlinking one and inviting a brand new manager.
 *
 * <p>Somebody invited here is made a guardian rather than an ordinary member, because that is what
 * they are being created for. The member kind is what carries the right to sign in, and a guardian
 * who cannot sign in cannot look after anybody, so creating one without it produces a person who
 * exists for a job they cannot start.
 */
export function useMemberManagers(
    memberId: Ref<number>,
    allMembers: Ref<StationMember[]>,
    fieldsForUserType: (userType: string) => ProfileField[],
    failure: Ref<Failure | null>,
) {
  const { t } = useI18n()

  const managers = ref<StationMember[]>([])
  const managerValues = ref<Map<number, Map<number, string>>>(new Map())
  const managerUserTypes = ref<Map<number, string>>(new Map())

  const managerUserTypesAsRoleMap = computed(() => {
    const result = new Map<number, string[]>()
    for (const [id, ut] of managerUserTypes.value) {
      result.set(id, ut ? [ut] : [])
    }
    return result
  })

  const availableManagers = computed(() => {
    const managerIds = new Set(managers.value.map(m => m.id))
    managerIds.add(memberId.value)
    return allMembers.value
        .filter(m => !managerIds.has(m.id))
        .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
  })

  function getManagerFields(mgrId: number): ProfileField[] {
    return fieldsForUserType(managerUserTypes.value.get(mgrId) ?? '')
  }

  function getManagerFieldValue(mgrId: number, fieldId: number): unknown {
    const vals = managerValues.value.get(mgrId)
    if (!vals) return ''
    const raw = vals.get(fieldId) ?? ''
    try { return JSON.parse(raw) } catch { return raw }
  }

  /**
   * Reads each manager's answers, one at a time so that one unreadable manager does not blank the rest.
   *
   * <p>Said out loud where any of them could not be read, because the panel then shows that manager with
   * empty fields, and an empty telephone number on a guardian reads as one nobody has given.
   */
  async function loadDetails(mgrs: StationMember[]) {
    const mgrVals = new Map<number, Map<number, string>>()
    const mgrTypes = new Map<number, string>()
    let unreadable: unknown = null
    for (const mgr of mgrs) {
      try {
        const [vals, memberData] = await Promise.all([
          profileFields.getValues(mgr.id),
          stationMembers.getMember(mgr.id),
        ])
        const fieldMap = new Map<number, string>()
        for (const v of vals) { fieldMap.set(v.fieldId, v.value ?? '') }
        mgrVals.set(mgr.id, fieldMap)
        mgrTypes.set(mgr.id, memberData.userType ?? '')
      } catch (e) {
        unreadable = e
      }
    }
    managerValues.value = mgrVals
    managerUserTypes.value = mgrTypes
    if (unreadable) {
      failure.value = {...describeFailure(unreadable, t), message: t('memberDetail.managerDetailsUnreadable')}
    }
  }

  async function linkManager(managerId: number) {
    failure.value = null
    try {
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, managerId] })
      managers.value = await stationMembers.getManagers(memberId.value)
      await loadDetails(managers.value)
    } catch (e) { failure.value = describeFailure(e, t) }
  }

  async function removeManager(mgrId: number) {
    failure.value = null
    try {
      const newIds = managers.value.filter(m => m.id !== mgrId).map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: newIds })
      managers.value = await stationMembers.getManagers(memberId.value)
    } catch (e) { failure.value = describeFailure(e, t) }
  }

  /**
   * Invites somebody and makes them this member's guardian, which is four writes behind one button.
   *
   * <p>The invitation is caught on its own because everything after it depends on an account that now
   * exists. A reader told only that it failed invites the same person again and is refused for an
   * address already in use, with nothing said about the half-made guardian sitting in the roll.
   *
   * <p>The same goes for the invited person not turning up in the roll afterwards, which used to end
   * the whole thing without a word: the account had been created and the screen looked as though the
   * button had done nothing at all.
   */
  async function createManager(data: { firstName: string; lastName: string; email: string; sendSetupMail?: boolean }) {
    failure.value = null

    let invitedId: number
    try {
      invitedId = (await members.invite({
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        sendSetupMail: data.sendSetupMail,
      })).id
    } catch (e) {
      failure.value = describeFailure(e, t)
      return
    }

    try {
      const updatedMembers = await stationMembers.listMembers()
      const newMember = updatedMembers.find(m => m.accountId === invitedId)
      if (!newMember) {
        failure.value = {
          kind: FailureKind.UNKNOWN,
          message: t('memberDetail.invitedButNotLinked'),
          guidance: t('memberDetail.invitedButNotLinkedGuidance'),
          reportable: true,
        }
        return
      }
      await stationMembers.setUserType(newMember.id, StationUserType.GUARDIAN)
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, newMember.id] })
      managers.value = await stationMembers.getManagers(memberId.value)
      await loadDetails(managers.value)
      allMembers.value = updatedMembers
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('memberDetail.invitedButNotLinked')}
    }
  }

  return {
    managers,
    managerValues,
    managerUserTypesAsRoleMap,
    availableManagers,
    getManagerFields,
    getManagerFieldValue,
    loadDetails,
    linkManager,
    removeManager,
    createManager,
  }
}
