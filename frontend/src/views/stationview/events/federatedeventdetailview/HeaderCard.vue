/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {FederatedEventDetail} from '@/api/events'
import {formatDate, formatTime} from '@/util/format'

const props = defineProps<{
  event: NonNullable<FederatedEventDetail['event']>
  publicFields: FederatedEventDetail['publicFields']
}>()

const {t} = useI18n()

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

function eventTypeBadge(type?: string): string {
  if (type === 'RECURRING') return t('federatedEventDetail.recurring')
  if (type === 'ONE_TIME') return t('federatedEventDetail.oneTime')
  return ''
}

/** How long the partner's series runs, where they gave it an end. */
const repeatEnd = computed(() => {
  if (props.event.repeatUntil) return t('events.repeatUntilLabel', {date: formatDate(props.event.repeatUntil)})
  if (props.event.repeatCount) return t('events.repeatCountLabel', {count: props.event.repeatCount})
  return ''
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div>
      <SubHeader>{{ props.event.name }}</SubHeader>
      <div class="flex items-center gap-2 mt-1 flex-wrap">
        <StationBadge :station-name="t('federatedEventDetail.partnerEvent')"/>
        <SecondaryBadge v-if="eventTypeBadge(props.event.eventType as string)">
          {{ eventTypeBadge(props.event.eventType as string) }}
        </SecondaryBadge>
        <InfoBadge v-if="props.event.requiresRegistration">
          {{ t('eventsUpcoming.registrationRequired') }}
        </InfoBadge>
        <SecondaryBadge v-if="repeatEnd" data-testid="federated-repeat-end">{{ repeatEnd }}</SecondaryBadge>
      </div>
    </div>

    <div v-if="props.event.startTime || props.event.dayOfWeek" class="flex items-center gap-3 text-sm">
      <span v-if="props.event.dayOfWeek && Number(props.event.dayOfWeek) > 0">
        <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-1 text-(--text-muted)"/>
        {{ dayNames[Number(props.event.dayOfWeek)] }}
      </span>
      <span v-if="props.event.startTime">
        <font-awesome-icon :icon="['fas', 'clock']" class="mr-1 text-(--text-muted)"/>
        {{ formatTime(props.event.startTime as string) }}
        <template v-if="props.event.endTime"> &ndash; {{ formatTime(props.event.endTime as string) }}</template>
      </span>
    </div>

    <p v-if="props.event.description" class="text-sm text-(--text-muted)">{{ props.event.description }}</p>

    <div v-if="props.publicFields.length > 0" class="space-y-1">
      <MutedText size="sm" class="font-medium">{{ t('federatedEventDetail.fields') }}</MutedText>
      <div class="flex flex-wrap gap-3 text-sm">
        <span v-for="field in props.publicFields" :key="field.id" class="text-(--text-muted)">
          <span class="font-medium">{{ field.name }}:</span> {{ field.value || '-' }}
        </span>
      </div>
    </div>
  </NeutralContainer>
</template>
