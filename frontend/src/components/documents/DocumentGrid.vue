/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {useAuthImages} from '@/composables/useAuthImage'
import DocumentTile from './DocumentTile.vue'
import {thumbnailUrl as stationThumbnailUrl} from '@/api/documents'
import type {MemberDocumentResponse} from '@/api/generated/schema'

const props = withDefaults(defineProps<{
  documents: MemberDocumentResponse[]
  /** Where a tile's picture comes from, or null where the door the reader comes through serves none. */
  thumbnailUrl?: ((documentId: number) => string) | null
  /** Whether each tile can be chosen, for removing several documents at once. A sealed one never can. */
  selectable?: boolean
  /** Whether the choice is held as it is, because the documents shown are about to be replaced. */
  choiceHeld?: boolean
}>(), {
  thumbnailUrl: (documentId: number) => stationThumbnailUrl(documentId),
  selectable: false,
  choiceHeld: false,
})

/** The documents chosen, by id. */
const selected = defineModel<number[]>('selected', {default: () => []})

const emit = defineEmits<{
  open: [document: MemberDocumentResponse]
}>()

const {t} = useI18n()

const {srcFor, load} = useAuthImages<number>()

const fetched = new Set<number>()

watch(() => props.documents, (documents) => {
  const pictureOf = props.thumbnailUrl
  if (!pictureOf) return
  for (const document of documents) {
    if (!document.hasThumbnail || fetched.has(document.id)) continue
    fetched.add(document.id)
    load(document.id, pictureOf(document.id))
  }
}, {immediate: true, deep: true})

function choose(documentId: number, chosen: boolean) {
  const others = selected.value.filter(id => id !== documentId)
  selected.value = chosen ? [...others, documentId] : others
}
</script>

<template>
  <EmptyHint v-if="props.documents.length === 0">{{ t('documents.none') }}</EmptyHint>
  <div v-else class="grid gap-3 grid-cols-2 sm:grid-cols-3 lg:grid-cols-4">
    <div v-for="document in props.documents" :key="document.id" class="relative">
      <div v-if="props.selectable && !document.sealed" class="absolute top-2 left-2 z-10">
        <CheckboxInput
            :model-value="selected.includes(document.id)"
            :aria-label="t('documents.select')"
            :disabled="props.choiceHeld"
            data-testid="document-select"
            @update:model-value="chosen => choose(document.id, chosen)"
        />
      </div>
      <DocumentTile :document="document" :thumbnail="srcFor(document.id)" @open="emit('open', $event)"/>
    </div>
  </div>
</template>
