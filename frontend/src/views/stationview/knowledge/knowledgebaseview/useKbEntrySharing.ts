/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { knowledgeBase, memberGroups, userTags } from '@/api'
import type { KbGrant } from '@/api/knowledgeBase'
import type { MemberGroup, UserTag } from '@/api/types'
import { emptyRestriction, type RestrictionSelection } from '@/components/input/restriction'
import { grantKey, groupKey, tagKey, userTypeKey, type GrantLevels } from './kbGrantLevels'
import type { KbEntryApi, KbEntryTarget } from './useKbEntryEditor'
import { describeFailure, FailureKind, type Failure } from '@/util/failure'

/**
 * Who may see one knowledge base entry: the audience it is restricted to and whether it stands on the
 * public wiki.
 *
 * <p>Separate from the entry's name and description, and reached from its own place rather than from the
 * bottom of the edit dialog. Who may see a thing is not a property of the thing the way its name is, and
 * hiding it inside editing is why nobody found it.
 *
 * <p>Everything is loaded when the dialog opens rather than kept in sync, so it always shows what is
 * stored even if the entry changed underneath the list. A failed load leaves the defaults in place: a
 * missing restriction reads as "no restriction", which is what an entry without one has.
 *
 * @param show  whether the dialog is open; opening triggers the load
 * @param entry the entry being shared
 * @param api   the endpoints of the entry's kind
 */
export function useKbEntrySharing(
  show: Ref<boolean>,
  entry: () => KbEntryTarget | null,
  api: KbEntryApi,
) {
  const { t } = useI18n()

  const restriction = ref<RestrictionSelection>(emptyRestriction())
  const grantLevels = ref<GrantLevels>({})
  const publicVisibility = ref<string>('default')
  const allGroups = ref<MemberGroup[]>([])
  const allTags = ref<UserTag[]>([])
  const failure = ref<Failure | null>(null)

  /**
   * Whether the audience currently stored is actually in hand.
   *
   * <p>A failed fetch used to be swallowed and the dialog opened showing the default, empty audience,
   * which is indistinguishable from an entry that restricts nobody. Saving then wrote that back, and
   * who may read the entry changed without the reader ever seeing what it had been.
   */
  const audienceLoaded = ref(false)

  watch(show, async (visible) => {
    const target = entry()
    if (!visible || !target) return
    restriction.value = emptyRestriction()
    grantLevels.value = {}
    publicVisibility.value = 'default'
    audienceLoaded.value = false
    failure.value = null

    try {
      const [groupList, tagList] = await Promise.all([memberGroups.listGroups(), userTags.listTags()])
      allGroups.value = groupList
      allTags.value = tagList
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('kb.audienceChoicesFailed')}
    }

    try {
      const [restrictions, visibility] = await Promise.all([
        api.getRestrictions(target.id),
        knowledgeBase.getPublicVisibility(api.visibilityKind, target.id),
      ])
      restriction.value = {
        userTypes: restrictions.userTypes ?? [],
        groupIds: restrictions.groupIds,
        tagIds: restrictions.tagIds,
        memberIds: [],
        mode: 'AND',
      }
      const levels: GrantLevels = {}
      for (const grant of restrictions.grants ?? []) {
        const key = grantKey(grant)
        if (key) levels[key] = grant.level ?? null
      }
      grantLevels.value = levels
      publicVisibility.value = visibility.visible === true
        ? 'public'
        : visibility.visible === false ? 'hidden' : 'default'
      audienceLoaded.value = true
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('kb.audienceLoadFailed')}
    }
  })

  /**
   * Turns the chosen audience into one grant per entry, each carrying the level chosen for it.
   * An entry the dialog has no level for keeps {@code null}, which leaves the station permission
   * in charge for that audience.
   */
  function buildGrants(): KbGrant[] {
    const levels = grantLevels.value
    return [
      ...restriction.value.userTypes.map(userType => ({userType, level: levels[userTypeKey(userType)] ?? null})),
      ...restriction.value.groupIds.map(groupId => ({groupId, level: levels[groupKey(groupId)] ?? null})),
      ...restriction.value.tagIds.map(tagId => ({tagId, level: levels[tagKey(tagId)] ?? null})),
    ]
  }

  /**
   * Writes the chosen audience, but only where the stored one was read first. Saving a dialog that
   * never managed to read it would silently replace who may see the entry with the default.
   */
  async function save(): Promise<boolean> {
    const target = entry()
    if (!target) return false
    if (!audienceLoaded.value) {
      failure.value = notLoaded()
      return false
    }
    const visible = publicVisibility.value === 'public'
      ? true
      : publicVisibility.value === 'hidden' ? false : null
    try {
      await Promise.all([
        api.setRestrictions(target.id, {
          userTypes: restriction.value.userTypes,
          groupIds: restriction.value.groupIds,
          tagIds: restriction.value.tagIds,
          memberIds: [],
          grants: buildGrants(),
        }),
        knowledgeBase.setPublicVisibility(api.visibilityKind, target.id, visible),
      ])
      show.value = false
      return true
    } catch (e) {
      failure.value = describeFailure(e, t)
      return false
    }
  }

  /** Refusing to save because the stored audience never arrived, which is nobody's mistake to report. */
  function notLoaded(): Failure {
    return {
      kind: FailureKind.UNKNOWN,
      message: t('kb.audienceNotSaved'),
      guidance: t('kb.audienceNotSavedGuidance'),
      reportable: false,
    }
  }

  return {
    restriction,
    grantLevels,
    publicVisibility,
    allGroups,
    allTags,
    audienceLoaded,
    failure,
    save,
  }
}
