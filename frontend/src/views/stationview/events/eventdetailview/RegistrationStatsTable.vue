/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import RegistrationStatsRow from './registrationstatstable/RegistrationStatsRow.vue'
import type {EventRegistrationField} from '@/api/events'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'

const props = defineProps<{
  fields?: EventRegistrationField[]
  registrations: EventRegistrationEntry[]
  stats: MemberRegistrationStats[]
  showActions?: boolean
  /** Whether the reader may put an answer right, which is whoever runs the appointment. */
  canEditAnswers?: boolean
}>()

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
  editAnswers: [registrationId: number]
}>()

const {t} = useI18n()

const sortedRegistrations = computed(() => {
  return [...props.registrations].sort((a, b) => {
    const sa = props.stats.find(s => s.memberId === a.memberId)
    const sb = props.stats.find(s => s.memberId === b.memberId)
    return (sb?.fairnessScore ?? 0) - (sa?.fairnessScore ?? 0)
  })
})

function getStats(memberId: number): MemberRegistrationStats | undefined {
  return props.stats.find(s => s.memberId === memberId)
}
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm border-collapse">
      <thead v-if="stats.length > 0">
      <tr class="border-b border-(--border) text-left text-xs text-(--text-muted) uppercase">
        <th class="p-2">{{ t('registrationStats.member') }}</th>
        <th class="p-2 text-center">{{ t('registrationStats.score') }}</th>
        <th class="p-2 text-center">{{ t('registrationStats.accepted') }}</th>
        <th class="p-2 text-center">{{ t('registrationStats.denied') }}</th>
        <th class="p-2 text-center">{{ t('registrationStats.rate') }}</th>
        <th class="p-2 text-center">{{ t('eventsRegistrations.date') }}</th>
        <th v-if="showActions" class="p-2"></th>
      </tr>
      </thead>
      <tbody>
      <RegistrationStatsRow
          v-for="reg in sortedRegistrations"
          :key="reg.id"
          :registration="reg"
          :fields="fields"
          :stats="getStats(reg.memberId)"
          :show-actions="showActions"
          :can-edit-answers="canEditAnswers"
          @accept="emit('accept', $event)"
          @deny="emit('deny', $event)"
          @edit-answers="emit('editAnswers', $event)"
      />
      </tbody>
    </table>
  </div>
</template>
