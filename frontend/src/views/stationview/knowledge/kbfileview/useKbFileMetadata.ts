/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { knowledgeBase } from '@/api'
import type { KbFile, KbTag } from '@/api/knowledgeBase'

/**
 * The tags, related files and description that describe a knowledge base file.
 *
 * All three are replaced rather than patched: the endpoints take the complete set, so adding one
 * tag means sending the tags the file already has alongside it. Each write returns the stored
 * state, which is what is kept - the server owns the ordering and the tag identities.
 *
 * @param file             the file being described, reloaded after a description change
 * @param lastEditedByName the "edited by" label, which a description change also updates
 */
export function useKbFileMetadata(file: Ref<KbFile | null>, lastEditedByName: Ref<string | null>) {
  const fileTags = ref<KbTag[]>([])
  const allStationTags = ref<KbTag[]>([])
  const relatedFiles = ref<KbFile[]>([])

  const editingDescription = ref(false)
  const editDescriptionValue = ref('')

  async function addTag(name: string) {
    if (!file.value) return
    fileTags.value = await knowledgeBase.setFileTags(file.value.id, [...fileTags.value.map(t => t.name), name])
    allStationTags.value = await knowledgeBase.listTags()
  }

  async function removeTag(tagName: string) {
    if (!file.value) return
    const remaining = fileTags.value.map(t => t.name).filter(n => n !== tagName)
    fileTags.value = await knowledgeBase.setFileTags(file.value.id, remaining)
  }

  async function addRelatedFile(targetId: number) {
    if (!file.value) return
    relatedFiles.value = await knowledgeBase.setRelatedFiles(
      file.value.id, [...relatedFiles.value.map(f => f.id), targetId])
  }

  async function removeRelatedFile(targetId: number) {
    if (!file.value) return
    const remaining = relatedFiles.value.map(f => f.id).filter(id => id !== targetId)
    relatedFiles.value = await knowledgeBase.setRelatedFiles(file.value.id, remaining)
  }

  function startEditDescription() {
    editingDescription.value = true
    editDescriptionValue.value = file.value?.description ?? ''
  }

  async function saveDescription() {
    if (!file.value) return
    await knowledgeBase.updateFile(file.value.id, {
      name: file.value.name,
      description: editDescriptionValue.value,
    })
    const reloaded = await knowledgeBase.getFile(file.value.id)
    file.value = reloaded.file
    lastEditedByName.value = reloaded.lastEditedByName
    editingDescription.value = false
  }

  return {
    fileTags,
    allStationTags,
    relatedFiles,
    editingDescription,
    editDescriptionValue,
    addTag,
    removeTag,
    addRelatedFile,
    removeRelatedFile,
    startEditDescription,
    saveDescription,
  }
}
