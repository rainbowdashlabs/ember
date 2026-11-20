/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {EventGroupDetails, EventGroupPartState} from '@/api/generated/schema'
import {keepsWaitingList} from '@/util/eventGroupChoice'
import {nonNegative} from '../templateeditview/signUpRules'
import {useEventGroupDetails} from './useEventGroupDetails'
import QueueOrderField from '../eventshared/QueueOrderField.vue'
import RegistrationDeadlineInput from '../eventshared/RegistrationDeadlineInput.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

/**
 * How signing up for an event group works: its places for people and per appointment, the deadline,
 * whether sign-ups are confirmed, and the minimum of accepted sign-ups with the days before the first
 * part it must be reached by. A deadline before the start counts from the first part's start. The
 * days are only asked once a minimum is given, and the waiting list order once places are limited.
 */
const details = defineModel<EventGroupDetails>({required: true})

const props = withDefaults(defineProps<{
  /** The saved event group's parts, a part with places of its own keeping a waiting list too. */
  parts?: readonly (Pick<EventGroupPartState, 'places'> & Partial<Pick<EventGroupPartState, 'startTime'>>)[]
  /** The station's time zone, which the deadline of the first part is worked out on. */
  timeZone?: string | null
}>(), {parts: () => [], timeZone: null})

const {limit, deadline, confirm, minRegistrations, thresholdDays, partLimit, queueOrder} = useEventGroupDetails(details)

/** Whether anything limits places, which is what a waiting list needs. */
const keepsQueue = computed(() => keepsWaitingList(details.value, props.parts))

/** The start of the first part, which a deadline before the start counts from. */
const firstStart = computed(() => props.parts
    .map(part => part.startTime)
    .filter((start): start is string => !!start)
    .sort((a, b) => new Date(a).getTime() - new Date(b).getTime())[0] ?? null)

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
        <FieldLabel for="event-group-part-limit">{{ t('eventGroups.partLimit') }}</FieldLabel>
        <NumberInput
            id="event-group-part-limit"
            :model-value="partLimit"
            min="0"
            :placeholder="t('events.registrationLimitHint')"
            data-testid="event-group-part-limit"
            @update:model-value="value => partLimit = nonNegative(value)"
        />
        <FieldHint>{{ t('eventGroups.partLimitHint') }}</FieldHint>
      </div>
    </div>
    <QueueOrderField v-if="keepsQueue" v-model="queueOrder"/>
    <RegistrationDeadlineInput v-model="deadline" :time-zone="timeZone" :next-start="firstStart"/>
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
