/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {PublicSearchResult} from '@/api/publicKb'
import type {KbFile} from '@/api/knowledgeBase'
import {fileIcon} from '@/util/kbFileIcon'

defineProps<{
  searching: boolean
  searchResults: PublicSearchResult[]
}>()

const emit = defineEmits<{
  (e: 'open', file: KbFile): void
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <SectionHeader class="text-lg font-semibold mb-3">{{ t('publicKb.searchResults') }}</SectionHeader>
    <Spinner v-if="searching"/>
    <p v-else-if="searchResults.length === 0" class="text-[var(--text-muted)]">
      {{ t('publicKb.noResults') }}
    </p>
    <div v-else class="flex flex-col gap-2">
      <NeutralContainer
          v-for="result in searchResults"
          :key="result.file.id"
          class="cursor-pointer hover:border-[var(--primary)] transition-colors"
          @click="emit('open', result.file)"
      >
        <div class="flex items-start gap-3 p-2">
          <font-awesome-icon :icon="fileIcon(result.file)" class="text-xl text-[var(--primary)] mt-0.5"/>
          <div class="flex-1 min-w-0">
            <span class="text-sm font-medium">{{ result.file.name }}</span>
            <p v-if="result.snippet" class="text-xs text-[var(--text-muted)] mt-1 line-clamp-2" v-html="result.snippet"/>
            <p v-else-if="result.file.description" class="text-xs text-[var(--text-muted)] mt-1 truncate">
              {{ result.file.description }}
            </p>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
