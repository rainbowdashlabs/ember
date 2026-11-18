/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import {ref} from 'vue'
import BareButton from '@/components/button/BareButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import RegistrationStatsTable from '../eventdetailview/RegistrationStatsTable.vue'
import type {RegistrationResponse, RegistrationStatsResponse} from '@/api/generated/schema'

/**
 * One group of a date's sign-ups, by their status. The declined answers need no decision, so their
 * group shows only its heading and count until it is opened, and lists them without the place ranking.
 */
const props = defineProps<{
  icon: string
  title: string
  registrations: RegistrationResponse[]
  stats: RegistrationStatsResponse[]
  showActions?: boolean
  /** Whether these are the declined answers. */
  declined?: boolean
}>()

const open = ref(!props.declined)

const emit = defineEmits<{
  accept: [regId: number]
  deny: [regId: number]
}>()
</script>

<template>
  <section v-if="registrations.length > 0" class="space-y-2">
    <BareButton v-if="declined" class="flex items-center gap-1 cursor-pointer" :aria-expanded="open"
                data-testid="status-section-toggle" @click="open = !open">
      <SubHeader>
        <AppIcon :icon="['fas', open ? 'chevron-down' : 'chevron-right']" class="mr-1"/>
        <AppIcon :icon="['fas', icon]" class="mr-1"/>
        {{ title }} ({{ registrations.length }})
      </SubHeader>
    </BareButton>
    <SubHeader v-else>
      <AppIcon :icon="['fas', icon]" class="mr-1"/>
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
