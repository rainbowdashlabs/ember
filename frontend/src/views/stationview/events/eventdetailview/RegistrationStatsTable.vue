/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'

const props = defineProps<{
  registrations: EventRegistrationEntry[]
  stats: MemberRegistrationStats[]
  showActions?: boolean
}>()

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
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
      <tr v-for="reg in sortedRegistrations" :key="reg.id" class="border-b border-(--border) last:border-0">
        <td class="p-2"><MemberName :identity="reg.memberIdentity ?? null"/></td>
        <template v-if="getStats(reg.memberId)">
          <td class="p-2 text-center font-bold" :class="getStats(reg.memberId)!.priority === 'HIGH' ? 'text-success' : getStats(reg.memberId)!.priority === 'MEDIUM' ? 'text-info' : ''">
            {{ getStats(reg.memberId)!.fairnessScore }}
          </td>
          <td class="p-2 text-center"><SuccessBadge>{{ getStats(reg.memberId)!.accepted }}</SuccessBadge></td>
          <td class="p-2 text-center">
            <ErrorBadge v-if="getStats(reg.memberId)!.denied > 0">{{ getStats(reg.memberId)!.denied }}</ErrorBadge>
            <span v-else>0</span>
          </td>
          <td class="p-2 text-center">{{ Math.round(getStats(reg.memberId)!.acceptRate * 100) }}%</td>
        </template>
        <template v-else>
          <td class="p-2 text-center text-(--text-muted)" colspan="4">–</td>
        </template>
        <td class="p-2 text-center text-xs text-(--text-muted)">{{ reg.eventDate }}</td>
        <td v-if="showActions" class="p-2">
          <div class="flex items-center gap-1 justify-end">
            <PrimaryButton @click="emit('accept', reg.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('eventsRegistrations.accept') }}
            </PrimaryButton>
            <ErrorButton @click="emit('deny', reg.id)">
              <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
              {{ t('eventsRegistrations.deny') }}
            </ErrorButton>
          </div>
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</template>
