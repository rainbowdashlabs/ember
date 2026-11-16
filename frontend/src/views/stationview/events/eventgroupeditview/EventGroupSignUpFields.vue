/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {EventGroupDetails} from '@/api/generated/schema'
import {nonNegative} from '../templateeditview/signUpRules'
import {useEventGroupDetails} from './useEventGroupDetails'

/**
 * How signing up for an event group works: its places, the deadline, whether sign-ups are confirmed,
 * and the minimum of accepted sign-ups with the days before the first part it must be reached by. The
 * days are only asked once a minimum is given.
 */
const details = defineModel<EventGroupDetails>({required: true})

const {limit, deadline, confirm, minRegistrations, thresholdDays} = useEventGroupDetails(details)

const {t} = useI18n()

const labelId = useId()
</script>

<template>
  <div class="space-y-4" data-testid="event-group-sign-up">
    <SubHeader>{{ t('events.registration') }}</SubHeader>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel for="event-group-limit">{{ t('eventGroups.limit') }}</FieldLabel>
        <NumberInput id="event-group-limit" v-model="limit" :placeholder="t('events.registrationLimitHint')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel for="event-group-deadline">{{ t('eventGroups.deadlineField') }}</FieldLabel>
        <DateTimeInput id="event-group-deadline" v-model="deadline"/>
      </div>
    </div>
    <div class="flex items-center justify-between">
      <span :id="`${labelId}-confirm`" class="text-sm font-medium">{{ t('eventGroups.confirmationField') }}</span>
      <ToggleInput v-model="confirm" :aria-labelledby="`${labelId}-confirm`"/>
    </div>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel for="event-group-minimum">{{ t('eventGroups.minimumField') }}</FieldLabel>
        <NumberInput
            id="event-group-minimum"
            :model-value="minRegistrations"
            min="0"
            data-testid="event-group-minimum"
            @update:model-value="value => minRegistrations = nonNegative(value)"
        />
      </div>
      <div v-if="minRegistrations" class="space-y-1">
        <div class="flex items-center gap-1">
          <FieldLabel for="event-group-threshold">{{ t('eventGroups.thresholdField') }}</FieldLabel>
          <HintIcon :text="t('eventGroups.minimumHint')"/>
        </div>
        <NumberInput
            id="event-group-threshold"
            :model-value="thresholdDays"
            min="0"
            :placeholder="t('events.thresholdDaysPlaceholder')"
            data-testid="event-group-threshold"
            @update:model-value="value => thresholdDays = nonNegative(value)"
        />
      </div>
    </div>
  </div>
</template>
