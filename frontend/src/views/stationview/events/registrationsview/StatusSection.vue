/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import RegistrationStatsTable from '../eventdetailview/RegistrationStatsTable.vue'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'

defineProps<{
  icon: string
  title: string
  registrations: EventRegistrationEntry[]
  stats: MemberRegistrationStats[]
  showActions?: boolean
}>()

const emit = defineEmits<{
  accept: [regId: number]
  deny: [regId: number]
}>()
</script>

<template>
  <section v-if="registrations.length > 0" class="space-y-2">
    <SubHeader>
      <font-awesome-icon :icon="['fas', icon]" class="mr-1"/>
      {{ title }} ({{ registrations.length }})
    </SubHeader>
    <RegistrationStatsTable
        :registrations="registrations"
        :stats="stats"
        :show-actions="showActions ?? false"
        @accept="(id) => emit('accept', id)"
        @deny="(id) => emit('deny', id)"
    />
  </section>
</template>
