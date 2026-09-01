/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import RegistrationFieldAnswers from '../RegistrationFieldAnswers.vue'
import type {EventRegistrationEntry, EventRegistrationField, MemberRegistrationStats} from '@/api/events'
import {formatDate} from '@/util/format'

/** One member's line on the ranking: what they answered, how often they have been let in, and the decision. */
const props = defineProps<{
  registration: EventRegistrationEntry
  fields?: EventRegistrationField[]
  stats?: MemberRegistrationStats
  showActions?: boolean
  canEditAnswers?: boolean
}>()

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
  editAnswers: [registrationId: number]
}>()

const {t} = useI18n()

/** The colour the ranking is read by: the ones with the strongest claim to a place stand out. */
function scoreClass(): string {
  if (props.stats?.priority === 'HIGH') return 'text-success'
  if (props.stats?.priority === 'MEDIUM') return 'text-info'
  return ''
}
</script>

<template>
  <tr class="border-b border-(--border) last:border-0">
    <td class="p-2">
      <MemberName :identity="registration.memberIdentity ?? null"/>
      <div class="flex items-center gap-2 flex-wrap">
        <RegistrationFieldAnswers :fields="fields ?? []" :values="registration.fields" class="mt-1"/>
        <EditButton
            v-if="canEditAnswers"
            :data-testid="`edit-answers-${registration.id}`"
            @click="emit('editAnswers', registration.id)"
        />
      </div>
    </td>
    <template v-if="stats">
      <td class="p-2 text-center font-bold" :class="scoreClass()">{{ stats.fairnessScore }}</td>
      <td class="p-2 text-center"><SuccessBadge>{{ stats.accepted }}</SuccessBadge></td>
      <td class="p-2 text-center">
        <ErrorBadge v-if="stats.denied > 0">{{ stats.denied }}</ErrorBadge>
        <span v-else>0</span>
      </td>
      <td class="p-2 text-center">{{ Math.round(stats.acceptRate * 100) }}%</td>
    </template>
    <template v-else>
      <td class="p-2 text-center text-(--text-muted)" colspan="4">–</td>
    </template>
    <td class="p-2 text-center text-xs text-(--text-muted)">{{ formatDate(registration.eventDate) }}</td>
    <td v-if="showActions" class="p-2">
      <div class="flex items-center gap-1 justify-end">
        <PrimaryButton @click="emit('accept', registration.id)">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
          {{ t('eventsRegistrations.accept') }}
        </PrimaryButton>
        <ErrorButton @click="emit('deny', registration.id)">
          <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
          {{ t('eventsRegistrations.deny') }}
        </ErrorButton>
      </div>
    </td>
  </tr>
</template>
