/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {formatDate} from '@/util/format'
import type {MemberDocument} from '@/api/memberDocuments'
import {formatSize, iconFor} from './documentIcon'

/**
 * One document as the store shows it: the picture of it where there is one, its title, and the
 * words it was filed under.
 */
const props = defineProps<{
  document: MemberDocument
  /** The picture of it, already fetched, or null while there is none. */
  thumbnail?: string | null
}>()

const emit = defineEmits<{
  open: [document: MemberDocument]
}>()

const {t} = useI18n()

const icon = computed(() => iconFor(props.document.mimeType))
</script>

<template>
  <button
      type="button"
      data-testid="document-tile"
      class="text-left rounded-theme border border-bg-light-accent dark:border-bg-dark-accent overflow-hidden hover:border-primary transition-colors"
      @click="emit('open', props.document)"
  >
    <div class="aspect-[4/3] bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 flex items-center justify-center overflow-hidden">
      <img v-if="props.thumbnail" :src="props.thumbnail" :alt="props.document.title" class="h-full w-full object-cover"/>
      <font-awesome-icon v-else :icon="['fas', icon]" class="h-10 w-10 text-(--text-muted)"/>
    </div>
    <div class="p-3 space-y-1">
      <div class="font-medium text-sm truncate" :title="props.document.title">{{ props.document.title }}</div>
      <MutedText size="sm">{{ formatSize(props.document.sizeBytes) }} · {{ formatDate(props.document.createdAt) }}</MutedText>
      <div v-if="props.document.tags.length > 0" class="flex flex-wrap gap-1 pt-1">
        <PrimaryBadge v-for="tag in props.document.tags" :key="tag">{{ tag }}</PrimaryBadge>
      </div>
      <div class="flex flex-wrap gap-2 pt-1 text-xs text-(--text-muted)">
        <span v-if="props.document.hidden">
          <font-awesome-icon :icon="['fas', 'eye-slash']" class="mr-1"/>{{ t('documents.hidden') }}
        </span>
        <span v-if="props.document.keepOnArchive">{{ t('documents.kept') }}</span>
      </div>
    </div>
  </button>
</template>
