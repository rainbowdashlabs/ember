/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {StationEvent} from '@/api/types'
import MutedIcon from '@/components/display/MutedIcon.vue'

const {t} = useI18n()

defineProps<{
  events: StationEvent[]
}>()

const emit = defineEmits<{
  attendance: [event: StationEvent]
}>()

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div v-if="events.length > 0" class="space-y-3">
    <SectionHeader>{{ t('events.today') }}</SectionHeader>
    <div class="grid gap-3 sm:grid-cols-2">
      <PrimaryContainer v-for="ev in events" :key="ev.id" class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="font-semibold">{{ ev.name }}</span>
          <MutedIcon v-if="ev.restricted" :icon="['fas', 'lock']" class="ml-1"/>
          <span class="text-sm">{{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}</span>
        </div>
        <p v-if="ev.description" class="text-sm text-(--text-muted)">{{ ev.description }}</p>
        <PrimaryButton :icon="['fas', 'clipboard-user']" v-if="ev.templateId" @click="emit('attendance', ev)">
          {{ t('events.manageAttendance') }}
        </PrimaryButton>
      </PrimaryContainer>
    </div>
  </div>
</template>
