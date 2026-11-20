/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import {computed, ref} from 'vue'
import BareButton from '@/components/button/BareButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {useI18n} from 'vue-i18n'
import RegistrationStatsTable from '../eventdetailview/RegistrationStatsTable.vue'
import {queueableByAge} from '../eventdetailview/registrationGroups'
import type {RegistrationResponse, RegistrationStatsResponse} from '@/api/generated/schema'

/**
 * One group of a date's sign-ups, by their status. The declined answers need no decision, so their
 * group shows only its heading and count until it is opened, and lists them without the place ranking.
 * Where the date keeps a waiting list, sign-ups waiting for a decision can be put on it, one at a time
 * or all at once.
 */
const props = defineProps<{
  icon: string
  title: string
  registrations: RegistrationResponse[]
  stats: RegistrationStatsResponse[]
  showActions?: boolean
  /** Whether these are the declined answers. */
  declined?: boolean
  /** Whether the sign-ups can be put on the waiting list. */
  canQueue?: boolean
  busy?: boolean
}>()

const open = ref(!props.declined)

const emit = defineEmits<{
  accept: [regId: number]
  deny: [regId: number]
  queue: [regIds: number[]]
}>()

const {t} = useI18n()

/** Every sign-up of the group that can go on the waiting list, oldest first. */
const queueable = computed(() => queueableByAge(props.registrations))
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
    <div v-else class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>
        <AppIcon :icon="['fas', icon]" class="mr-1"/>
        {{ title }} ({{ registrations.length }})
      </SubHeader>
      <SecondaryButton
          v-if="canQueue && queueable.length > 1"
          compact
          :icon="['fas', 'list-ol']"
          :disabled="busy"
          data-testid="queue-all-pending"
          @click="emit('queue', queueable)"
      >
        {{ t('registrationQueue.putAllOnList') }}
      </SecondaryButton>
    </div>
    <RegistrationStatsTable
        v-if="open"
        :registrations="registrations"
        :stats="stats"
        :show-actions="showActions ?? false"
        :declined="declined ?? false"
        :can-queue="canQueue ?? false"
        @accept="(id) => emit('accept', id)"
        @deny="(id) => emit('deny', id)"
        @queue="(id) => emit('queue', [id])"
    />
  </section>
</template>
