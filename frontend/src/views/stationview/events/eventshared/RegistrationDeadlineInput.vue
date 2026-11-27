/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {formatTime, formatWeekdayDate, instantToLocalInput, localInputToInstant} from '@/util/format'
import {nonNegative} from '../templateeditview/signUpRules'
import {DeadlineAnchor, DeadlineKind, type RegistrationDeadline} from '@/api/generated/schema'
import {closesAt, MINUTES_PER_DAY, MINUTES_PER_HOUR, noDeadline, sameRule, zoneOrLocal} from './registrationDeadline'

/**
 * When sign-up closes: never, at one fixed moment, or a number of hours or days before each date's
 * start, either to the minute or at the midnight that begins that day.
 *
 * <p>The span is stored in minutes and shown in days where it is whole days, in hours otherwise. The
 * unit picked keeps the number typed, so "7" and then "Tage" means a week. Switching to "Vor Beginn"
 * starts at a day before, at midnight, or at whatever span was set before in this editor; switching to
 * "Fester Zeitpunkt" starts empty.
 *
 * <p>A template knows no date and so offers no fixed moment (`allowFixed`).
 *
 * <p>Given the start of the next date, the editor shows when that date closes, on the station's clock.
 * On a series a fixed moment closes every date at once, which a hint beside the choice shows.
 */
const rule = defineModel<RegistrationDeadline>({required: true})

const props = withDefaults(defineProps<{
  /** The station's IANA time zone, which shows where midnight is; the reader's own where none is known. */
  timeZone?: string | null
  /** The start of the next date, for the line showing when it closes. */
  nextStart?: string | null
  /** Whether the appointment repeats, which a fixed deadline closes all at once. */
  recurring?: boolean
  disabled?: boolean
  /** Whether a fixed moment is offered, which a template, knowing no date, cannot hold. */
  allowFixed?: boolean
}>(), {timeZone: null, nextStart: null, recurring: false, disabled: false, allowFixed: true})

const {t} = useI18n()
const id = useId()

type DeadlineUnit = 'HOURS' | 'DAYS'

/** The span and anchor last set for a relative deadline, which switching back to one restores. */
interface RelativeSpan {
  offsetMinutes: number
  anchor: DeadlineAnchor
}

const UNIT_MINUTES: Readonly<Record<DeadlineUnit, number>> = {HOURS: MINUTES_PER_HOUR, DAYS: MINUTES_PER_DAY}

/** Days where the span is whole days, hours otherwise. */
function unitFor(minutes: number | null): DeadlineUnit {
  return minutes != null && minutes % MINUTES_PER_DAY === 0 ? 'DAYS' : 'HOURS'
}

const unit = ref<DeadlineUnit>(unitFor(rule.value.offsetMinutes))

const lastRelative = ref<RelativeSpan>({
  offsetMinutes: rule.value.offsetMinutes ?? MINUTES_PER_DAY,
  anchor: rule.value.anchor ?? DeadlineAnchor.MIDNIGHT,
})

/** The rule this editor handed out last, which distinguishes its own edits from a rule replaced from outside. */
let editedHere: RegistrationDeadline | null = null

/** Hands out a changed rule as this editor's own edit. */
function setRule(next: RegistrationDeadline) {
  editedHere = next
  rule.value = next
}

/**
 * Keeps the unit fitting the rule. A rule that arrives from outside or has just become relative is
 * shown in the unit it is whole in; while it is being edited here, the unit chosen stays as long as it
 * still divides the span.
 */
watch(() => rule.value, (current, previous) => {
  if (current.kind !== DeadlineKind.RELATIVE || current.offsetMinutes == null) return
  lastRelative.value = {offsetMinutes: current.offsetMinutes, anchor: current.anchor ?? DeadlineAnchor.EXACT}
  const arrived = previous?.kind !== DeadlineKind.RELATIVE || !sameRule(current, editedHere)
  if (arrived || current.offsetMinutes % UNIT_MINUTES[unit.value] !== 0) unit.value = unitFor(current.offsetMinutes)
})

/** Switches the kind of deadline, starting each one from where this editor last left it. */
function chooseKind(kind: unknown) {
  if (kind === DeadlineKind.FIXED) {
    setRule({kind: DeadlineKind.FIXED, at: null, offsetMinutes: null, anchor: null})
  } else if (kind === DeadlineKind.RELATIVE) {
    unit.value = unitFor(lastRelative.value.offsetMinutes)
    setRule({kind: DeadlineKind.RELATIVE, at: null, ...lastRelative.value})
  } else {
    setRule(noDeadline())
  }
}

const amount = computed(() => (rule.value.offsetMinutes ?? 0) / UNIT_MINUTES[unit.value])

function setAmount(value: unknown) {
  setRule({...rule.value, offsetMinutes: (nonNegative(value) ?? 0) * UNIT_MINUTES[unit.value]})
}

/** Takes the number standing in the field in the new unit. */
function setUnit(value: unknown) {
  const kept = amount.value
  unit.value = value === 'DAYS' ? 'DAYS' : 'HOURS'
  setRule({...rule.value, offsetMinutes: Math.round(kept * UNIT_MINUTES[unit.value])})
}

function setAnchor(value: unknown) {
  setRule({...rule.value, anchor: value === DeadlineAnchor.MIDNIGHT ? DeadlineAnchor.MIDNIGHT : DeadlineAnchor.EXACT})
}

const fixedMoment = computed({
  get: () => instantToLocalInput(rule.value.at),
  set: (value: string) => { setRule({...rule.value, at: localInputToInstant(value) || null}) },
})

const nextClosing = computed(() => {
  if (rule.value.kind !== DeadlineKind.RELATIVE || !props.nextStart) return null
  const zone = zoneOrLocal(props.timeZone)
  const closing = closesAt(rule.value, props.nextStart, zone)?.toISOString()
  if (!closing) return null
  return `${formatWeekdayDate(closing, 'short', zone)}, ${formatTime(closing, zone)}`
})

const kinds = computed<DeadlineKind[]>(() => props.allowFixed ? [DeadlineKind.NONE, DeadlineKind.FIXED, DeadlineKind.RELATIVE] : [DeadlineKind.NONE, DeadlineKind.RELATIVE])
const kindLabel = computed<Record<DeadlineKind, string>>(() => ({
  [DeadlineKind.NONE]: t('events.deadlineRule.none'),
  [DeadlineKind.FIXED]: t('events.deadlineRule.fixed'),
  [DeadlineKind.RELATIVE]: t('events.deadlineRule.relative'),
}))
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center gap-1">
      <FieldLabel :for="`${id}-kind`">{{ t('events.registrationDeadline') }}</FieldLabel>
      <HintIcon :text="t('events.deadlineRule.afterHint')"/>
      <HintIcon
          v-if="recurring && rule.kind === DeadlineKind.FIXED"
          :text="t('events.deadlineRule.fixedOnSeriesHint')"
          data-testid="registration-deadline-series-hint"
      />
    </div>
    <SelectInput
        :id="`${id}-kind`"
        :model-value="rule.kind"
        :disabled="disabled"
        class="w-full"
        data-testid="registration-deadline-kind"
        @update:model-value="chooseKind"
    >
      <option v-for="kind in kinds" :key="kind" :value="kind">{{ kindLabel[kind] }}</option>
    </SelectInput>

    <DateTimeInput
        v-if="rule.kind === DeadlineKind.FIXED"
        v-model="fixedMoment"
        :disabled="disabled"
        :label="t('events.registrationDeadline')"
        data-testid="registration-deadline-at"
    />

    <template v-else-if="rule.kind === DeadlineKind.RELATIVE">
      <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
        <NumberInput
            :model-value="amount"
            :aria-label="t('events.deadlineRule.amount')"
            :disabled="disabled"
            min="0"
            data-testid="registration-deadline-amount"
            @update:model-value="setAmount"
        />
        <SelectInput
            :model-value="unit"
            :aria-label="t('events.deadlineRule.unit')"
            :disabled="disabled"
            class="w-full"
            data-testid="registration-deadline-unit"
            @update:model-value="setUnit"
        >
          <option value="HOURS">{{ t('events.deadlineRule.hours') }}</option>
          <option value="DAYS">{{ t('events.deadlineRule.days') }}</option>
        </SelectInput>
        <SelectInput
            :model-value="rule.anchor ?? DeadlineAnchor.EXACT"
            :aria-label="t('events.deadlineRule.anchor')"
            :disabled="disabled"
            class="col-span-2 w-full sm:col-span-1"
            data-testid="registration-deadline-anchor"
            @update:model-value="setAnchor"
        >
          <option :value="DeadlineAnchor.EXACT">{{ t('events.deadlineRule.exact') }}</option>
          <option :value="DeadlineAnchor.MIDNIGHT">{{ t('events.deadlineRule.midnight') }}</option>
        </SelectInput>
      </div>
      <MutedText v-if="nextClosing" size="sm" tag="p" data-testid="registration-deadline-next">
        {{ t('events.deadlineRule.nextDate', {when: nextClosing}) }}
      </MutedText>
    </template>
  </div>
</template>
