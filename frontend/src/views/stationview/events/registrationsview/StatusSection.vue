/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import BareButton from '@/components/button/BareButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import RegistrationStatsTable from '../eventdetailview/RegistrationStatsTable.vue'
import type {RegistrationResponse, RegistrationStatsResponse} from '@/api/generated/schema'

/**
 * One group of a date's sign-ups, by their status. A collapsed group shows only its heading and
 * count until it is opened, for answers that do not need a decision.
 */
const props = defineProps<{
  icon: string
  title: string
  registrations: RegistrationResponse[]
  stats: RegistrationStatsResponse[]
  showActions?: boolean
  collapsed?: boolean
  declined?: boolean
}>()

const open = ref(!props.collapsed)

const emit = defineEmits<{
  accept: [regId: number]
  deny: [regId: number]
}>()
</script>

<template>
  <section v-if="registrations.length > 0" class="space-y-2">
    <BareButton v-if="collapsed" class="flex items-center gap-1 cursor-pointer" :aria-expanded="open"
                data-testid="status-section-toggle" @click="open = !open">
      <SubHeader>
        <font-awesome-icon :icon="['fas', open ? 'chevron-down' : 'chevron-right']" class="mr-1"/>
        <font-awesome-icon :icon="['fas', icon]" class="mr-1"/>
        {{ title }} ({{ registrations.length }})
      </SubHeader>
    </BareButton>
    <SubHeader v-else>
      <font-awesome-icon :icon="['fas', icon]" class="mr-1"/>
      {{ title }} ({{ registrations.length }})
    </SubHeader>
    <RegistrationStatsTable
        v-if="open"
        :registrations="registrations"
        :stats="stats"
        :show-actions="showActions ?? false"
        :declined="declined ?? false"
        @accept="(id) => emit('accept', id)"
        @deny="(id) => emit('deny', id)"
    />
  </section>
</template>
