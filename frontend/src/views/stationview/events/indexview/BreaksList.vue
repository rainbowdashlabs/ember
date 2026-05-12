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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {EventBreak} from '@/api/types'

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
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('events.breaks') }}</SectionHeader>
      <div class="flex items-center gap-2">
        <SecondaryButton class="text-sm" @click="emit('importHolidays')">
          <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
          {{ t('events.importHolidays') }}
        </SecondaryButton>
        <PrimaryButton @click="emit('add')">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
          {{ t('events.addBreak') }}
        </PrimaryButton>
      </div>
    </div>

    <div v-if="breaks.length === 0" class="text-center text-(--text-muted) py-4">
      {{ t('events.noBreaks') }}
    </div>

    <div class="space-y-2">
      <NeutralContainer v-for="br in breaks" :key="br.id" class="flex items-center justify-between">
        <div>
          <span class="font-medium">{{ br.name }}</span>
          <span class="ml-2 text-sm text-(--text-muted)">{{ br.startDate }} – {{ br.endDate }}</span>
        </div>
        <div class="flex items-center gap-2">
          <EditButton @click="emit('edit', br)"/>
          <DeleteButton @click="emit('delete', br)"/>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
