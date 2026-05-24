/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {EventBreak} from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

defineProps<{
  breaks: EventBreak[]
}>()

const emit = defineEmits<{
  add: []
  edit: [eventBreak: EventBreak]
  delete: [eventBreak: EventBreak]
  importHolidays: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('events.breaks') }}</SectionHeader>
      <div class="flex items-center gap-2 flex-wrap">
        <SecondaryButton :icon="['fas', 'download']" @click="emit('importHolidays')">
          {{ t('events.importHolidays') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" @click="emit('add')">
          {{ t('events.addBreak') }}
        </PrimaryButton>
      </div>
    </div>

    <EmptyState compact v-if="breaks.length === 0">{{ t('events.noBreaks') }}</EmptyState>

    <div class="space-y-2">
      <NeutralContainer v-for="br in breaks" :key="br.id" class="flex items-center justify-between flex-wrap gap-2">
        <div>
          <span class="font-medium">{{ br.name }}</span>
          <MutedText size="sm" class="ml-2">{{ br.startDate }} – {{ br.endDate }}</MutedText>
        </div>
        <div class="flex items-center gap-2">
          <EditButton @click="emit('edit', br)"/>
          <DeleteButton @click="emit('delete', br)"/>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
