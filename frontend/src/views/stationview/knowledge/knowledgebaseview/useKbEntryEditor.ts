/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { KbGrant, KbTag } from '@/api/knowledgeBase'

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
 * The shared edit dialog behind a knowledge base file and folder: name, description and tags.
 *
 * Who may see the entry is not here. That lives in {@link useKbEntrySharing}, reached from its own
 * entry in the item menu, because an audience buried at the bottom of an edit dialog is an audience
 * nobody finds.
 *
 * Everything is loaded when the dialog opens rather than kept in sync, so the dialog always shows
 * what is currently stored even if the entry changed underneath the list. A failed load leaves the
 * defaults in place instead of reporting an error.
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
  const tags = ref<string[]>([])
  const error = ref('')

  function reset(target: KbEntryTarget) {
    editName.value = target.name
    editDescription.value = target.description
    tags.value = []
    error.value = ''
  }

  watch(show, async (visible) => {
    const target = entry()
    if (!visible || !target) return
    reset(target)

    try {
      tags.value = (await api.getTags(target.id)).map(tag => tag.name)
    } catch {
      error.value = ''
    }
  })

  /**
   * Saves every part of the dialog at once. {@code extra} carries writes only one kind has - the
   * folder icon - so they succeed or fail together with the rest.
   */
  async function save(extra: (id: number) => Promise<unknown>[] = () => []): Promise<boolean> {
    const target = entry()
    if (!target || !editName.value.trim()) return false
    try {
      await Promise.all([
        api.update(target.id, {name: editName.value.trim(), description: editDescription.value}),
        api.setTags(target.id, tags.value),
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
    tags,
    error,
    save,
  }
}
