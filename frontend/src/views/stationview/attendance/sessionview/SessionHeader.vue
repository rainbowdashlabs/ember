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
import {formatTime} from '@/util/format'

const {t} = useI18n()

const props = defineProps<{
  session: AttendanceSession
  readonly?: boolean
}>()

const emit = defineEmits<{
  updateTitle: [title: string]
  updateStartTime: [time: string]
  updateEndTime: [time: string]
}>()

</script>

<template>
  <!-- Session header (title + times when no event linked) -->
  <NeutralContainer v-if="!session.eventId" class="space-y-3">
    <div class="grid gap-3 sm:grid-cols-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.title') }}</FieldLabel>
        <TextInput v-if="!readonly" :model-value="session.title ?? ''" @update:model-value="emit('updateTitle', ($event as string) ?? '')"/>
        <span v-else class="text-sm">{{ session.title || '—' }}</span>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.startTime') }}</FieldLabel>
        <TimeShortInput v-if="!readonly"
            :model-value="formatTime(session.startTime)"
            @change="emit('updateStartTime', ($event.target as HTMLInputElement).value)"
        />
        <span v-else class="text-sm">{{ formatTime(session.startTime) || '—' }}</span>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.endTime') }}</FieldLabel>
        <TimeShortInput v-if="!readonly"
            :model-value="formatTime(session.endTime)"
            @change="emit('updateEndTime', ($event.target as HTMLInputElement).value)"
        />
        <span v-else class="text-sm">{{ formatTime(session.endTime) || '—' }}</span>
      </div>
    </div>
  </NeutralContainer>

  <!-- Event-linked session title -->
  <SectionHeader v-if="session.eventId && session.title">{{ session.title }}</SectionHeader>
</template>
