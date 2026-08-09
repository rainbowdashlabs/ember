/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {KbFile} from '@/api/knowledgeBase'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
  file: KbFile
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 mb-4">
    <SecondaryButton @click="emit('back')">
      <font-awesome-icon :icon="['fas', 'chevron-left']"/>
      {{ t('publicKb.backToBrowse') }}
    </SecondaryButton>
    <SectionHeader class="text-xl font-bold flex-1">{{ props.file.name }}</SectionHeader>
  </div>

  <MutedText tag="p" size="sm" v-if="props.file.description">
    {{ props.file.description }}
  </MutedText>

  <p v-if="props.file.updatedAt" class="text-xs text-[var(--text-muted)] mb-4">
    {{ formatDateTime(props.file.updatedAt) }}
  </p>
</template>
