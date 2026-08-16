/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { knowledgeBase, memberGroups, userTags } from '@/api'
import type { KbGrant, KbTag } from '@/api/knowledgeBase'
import type { MemberGroup, UserTag } from '@/api/types'
import { emptyRestriction, type RestrictionSelection } from '@/components/input/restriction'
import { grantKey, groupKey, tagKey, userTypeKey, type GrantLevels } from './kbGrantLevels'

interface RestrictionPayload {
  userTypes: string[]
  groupIds: number[]
  tagIds: number[]
  memberIds: number[]
  grants: KbGrant[]
}

/**
 * The endpoints of one knowledge base entry kind. Files and folders carry the same audience,
 * tags and visibility settings behind separate endpoints, so the kind is passed in rather than
 * branched on.
 */
export interface KbEntryApi {
  visibilityKind: 'files' | 'folders'
  getRestrictions: (
    id: number,
  ) => Promise<{userTypes?: string[]; groupIds: number[]; tagIds: number[]; grants?: KbGrant[]}>
  setRestrictions: (id: number, payload: RestrictionPayload) => Promise<unknown>
  getTags: (id: number) => Promise<KbTag[]>
  setTags: (id: number, names: string[]) => Promise<unknown>
  update: (id: number, payload: {name: string; description: string}) => Promise<unknown>
}

/** What a knowledge base entry's edit dialog needs to know about the entry it is editing. */
export interface KbEntryTarget {
  id: number
  name: string
  description: string
}

/**
 * The shared edit dialog behind a knowledge base file and folder: name, description, audience,
 * tags and public visibility.
 *
 * Everything is loaded when the dialog opens rather than kept in sync, so the dialog always shows
 * what is currently stored even if the entry changed underneath the list. A failed load leaves the
 * defaults in place instead of reporting an error — a missing restriction reads as "no
 * restriction", which is what an entry without one has.
 *
 * @param show   whether the dialog is open; opening triggers the load
 * @param entry  the entry being edited
 * @param api    the endpoints of the entry's kind
 */
export function useKbEntryEditor(
  show: Ref<boolean>,
  entry: () => KbEntryTarget | null,
  api: KbEntryApi,
) {
  const { t } = useI18n()

  const editName = ref('')
  const editDescription = ref('')
  const restriction = ref<RestrictionSelection>(emptyRestriction())
  const grantLevels = ref<GrantLevels>({})
  const tags = ref<string[]>([])
  const publicVisibility = ref<string>('default')
  const allGroups = ref<MemberGroup[]>([])
  const allTags = ref<UserTag[]>([])
  const error = ref('')

  function reset(target: KbEntryTarget) {
    editName.value = target.name
    editDescription.value = target.description
    restriction.value = emptyRestriction()
    grantLevels.value = {}
    tags.value = []
    publicVisibility.value = 'default'
    error.value = ''
  }

  watch(show, async (visible) => {
    const target = entry()
    if (!visible || !target) return
    reset(target)

    try {
      const [groupList, tagList] = await Promise.all([memberGroups.listGroups(), userTags.listTags()])
      allGroups.value = groupList
      allTags.value = tagList
    } catch {
      error.value = ''
    }

    try {
      const [restrictions, entryTags, visibility] = await Promise.all([
        api.getRestrictions(target.id),
        api.getTags(target.id),
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
      tags.value = entryTags.map(tag => tag.name)
      publicVisibility.value = visibility.visible === true
        ? 'public'
        : visibility.visible === false ? 'hidden' : 'default'
    } catch {
      error.value = ''
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
   * Saves every part of the dialog at once. {@code extra} carries writes only one kind has — the
   * folder icon — so they succeed or fail together with the rest.
   */
  async function save(extra: (id: number) => Promise<unknown>[] = () => []): Promise<boolean> {
    const target = entry()
    if (!target || !editName.value.trim()) return false
    const visible = publicVisibility.value === 'public'
      ? true
      : publicVisibility.value === 'hidden' ? false : null
    try {
      await Promise.all([
        api.update(target.id, {name: editName.value.trim(), description: editDescription.value}),
        api.setRestrictions(target.id, {
          userTypes: restriction.value.userTypes,
          groupIds: restriction.value.groupIds,
          tagIds: restriction.value.tagIds,
          memberIds: [],
          grants: buildGrants(),
        }),
        api.setTags(target.id, tags.value),
        knowledgeBase.setPublicVisibility(api.visibilityKind, target.id, visible),
        ...extra(target.id),
      ])
      show.value = false
      return true
    } catch {
      error.value = t('common.error')
      return false
    }
  }

  return {
    editName,
    editDescription,
    restriction,
    grantLevels,
    tags,
    publicVisibility,
    allGroups,
    allTags,
    error,
    save,
  }
}
