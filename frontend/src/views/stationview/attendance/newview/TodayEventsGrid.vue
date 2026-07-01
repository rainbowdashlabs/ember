/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {StationEvent} from '@/api/types'

const props = defineProps<{
  events: StationEvent[]
  templateName: (id: number) => string
  formatTime: (iso?: string) => string
}>()

const emit = defineEmits<{
  (e: 'select', ev: StationEvent): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('attendanceNew.todayEvents') }}</SubHeader>
    <div class="grid gap-3 sm:grid-cols-2">
      <PrimaryContainer v-for="ev in props.events" :key="ev.id"
                        class="space-y-2 cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                        @click="emit('select', ev)">
        <div class="flex items-center justify-between">
          <span class="font-semibold">{{ ev.name }}</span>
          <span class="text-sm">{{ props.formatTime(ev.startTime) }} – {{ props.formatTime(ev.endTime) }}</span>
        </div>
        <p v-if="ev.description" class="text-sm text-(--text-muted)">{{ ev.description }}</p>
        <p class="text-xs text-(--text-muted)">
          {{ t('attendanceNew.template') }}: {{ props.templateName(ev.templateId!) }}
        </p>
      </PrimaryContainer>
    </div>
  </div>
</template>
