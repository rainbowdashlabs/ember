/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import {useAuthImages} from '@/composables/useAuthImage'
import DocumentTile from './DocumentTile.vue'
import {thumbnailUrl, type MemberDocument} from '@/api/memberDocuments'

/**
 * The documents as tiles, each with the picture that was made of it.
 *
 * <p>The pictures come from an endpoint that wants a token, so they are fetched rather than
 * pointed at, which is why the grid rather than the tile owns them.
 */
const props = defineProps<{ documents: MemberDocument[] }>()

const emit = defineEmits<{
  open: [document: MemberDocument]
}>()

const {t} = useI18n()

const {srcFor, load} = useAuthImages<number>()

/**
 * Fetches the pictures that are not there yet, and only those. Fetching them all again on every
 * change would take the tiles blank and fill them once more, which is what makes a search flicker.
 */
const fetched = new Set<number>()

watch(() => props.documents, (documents) => {
  for (const document of documents) {
    if (!document.hasThumbnail || fetched.has(document.id)) continue
    fetched.add(document.id)
    load(document.id, thumbnailUrl(document.id))
  }
}, {immediate: true, deep: true})
</script>

<template>
  <EmptyHint v-if="props.documents.length === 0">{{ t('documents.none') }}</EmptyHint>
  <div v-else class="grid gap-3 grid-cols-2 sm:grid-cols-3 lg:grid-cols-4">
    <DocumentTile
        v-for="document in props.documents"
        :key="document.id"
        :document="document"
        :thumbnail="srcFor(document.id)"
        @open="emit('open', $event)"
    />
  </div>
</template>
