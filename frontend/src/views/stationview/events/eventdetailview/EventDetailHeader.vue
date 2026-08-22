/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {isRecurringEvent, type StationEvent} from '@/api/events'

const props = defineProps<{
  event: StationEvent
  canManageEvents: boolean
}>()

const emit = defineEmits<{
  (e: 'cancel'): void
}>()

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()

function goBack() {
  router.push({name: props.canManageEvents ? 'events' : 'events-upcoming'})
}

function goEdit() {
  router.push({name: eventRoutes.edit, params: {id: props.event.id}})
}
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-3">
    <div class="flex items-center gap-3">
      <SubHeader>{{ event.name }}</SubHeader>
      <SecondaryBadge v-if="isRecurringEvent(event.eventType)">
        <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>{{ t('events.typeRecurring') }}
      </SecondaryBadge>
      <SecondaryBadge v-else>{{ t('events.typeOneTime') }}</SecondaryBadge>
      <ErrorBadge v-if="event.cancelled">{{ t('events.cancelled') }}</ErrorBadge>
    </div>
    <div class="flex items-center gap-2">
      <SecondaryButton @click="goBack"><font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>{{ t('common.back') }}</SecondaryButton>
      <ErrorButton v-if="canManageEvents && !event.cancelled" @click="emit('cancel')"><font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>{{ t('events.cancelEvent') }}</ErrorButton>
      <PrimaryButton v-if="canManageEvents" @click="goEdit"><font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>{{ t('events.editEvent') }}</PrimaryButton>
    </div>
  </div>
</template>
