/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import type {AttendanceSession} from '@/api/types'

const {t} = useI18n()

defineProps<{
  session: AttendanceSession
}>()

const emit = defineEmits<{
  updateTitle: [title: string]
  updateStartTime: [time: string]
  updateEndTime: [time: string]
}>()

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <!-- Session header (title + times when no event linked) -->
  <NeutralContainer v-if="!session.eventId" class="space-y-3">
    <div class="grid gap-3 sm:grid-cols-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.title') }}</FieldLabel>
        <TextInput :model-value="session.title ?? ''" @update:model-value="emit('updateTitle', ($event as string) ?? '')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.startTime') }}</FieldLabel>
        <TimeShortInput
            :model-value="formatTime(session.startTime)"
            @change="emit('updateStartTime', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.endTime') }}</FieldLabel>
        <TimeShortInput
            :model-value="formatTime(session.endTime)"
            @change="emit('updateEndTime', ($event.target as HTMLInputElement).value)"
        />
      </div>
    </div>
  </NeutralContainer>

  <!-- Event-linked session title -->
  <SectionHeader v-if="session.eventId && session.title">{{ session.title }}</SectionHeader>
</template>
