/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectInput from '@/components/input/select/MultiSelectInput.vue'
import {StationUserTypeLabels} from '@/api/types'
import {
  ExpiryState,
  RuleNeed,
  RuleUnit,
  StationUserType,
  type RuleValue,
} from '@/api/generated/schema'
import {emptyValue} from './ruleDraft'

/**
 * The value of one check, shaped by what its operator needs: answers of a choice, user types, expiry states, a
 * number or a range, a text, a date, a duration or a number of years. An operator that needs nothing shows
 * nothing.
 */
const props = defineProps<{
  need: RuleNeed
  value: RuleValue | null
  /** The answers a choice field offers. */
  answers: readonly string[]
}>()

const emit = defineEmits<{
  change: [value: RuleValue]
}>()

const {t} = useI18n()

const current = computed(() => props.value ?? emptyValue())

function set(part: Partial<RuleValue>) {
  emit('change', {...current.value, ...part})
}

const answerOptions = computed(() => props.answers.map(answer => ({value: answer, label: answer})))
const typeOptions = computed(() => Object.values(StationUserType)
    .map(type => ({value: type, label: StationUserTypeLabels[type]})))
const stateOptions = computed(() => Object.values(ExpiryState)
    .map(state => ({value: state, label: t(`memberRules.states.${state}`)})))
const units = [RuleUnit.DAYS, RuleUnit.MONTHS, RuleUnit.YEARS]

const answersModel = computed({
  get: () => current.value.answers ?? [],
  set: answers => set({answers}),
})
const typesModel = computed({
  get: () => current.value.userTypes ?? [],
  set: types => set({userTypes: types as StationUserType[]}),
})
const statesModel = computed({
  get: () => current.value.states ?? [],
  set: states => set({states: states as ExpiryState[]}),
})
const unitModel = computed({
  get: () => current.value.unit ?? RuleUnit.MONTHS,
  set: unit => set({unit: unit as RuleUnit}),
})
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <MultiSelectInput v-if="need === RuleNeed.ANSWERS" v-model="answersModel" :options="answerOptions" inline
                      role="group" :aria-label="t('memberRules.valueAnswers')"/>
    <MultiSelectInput v-else-if="need === RuleNeed.USER_TYPES" v-model="typesModel" :options="typeOptions" inline
                      role="group" :aria-label="t('memberRules.valueUserTypes')"/>
    <MultiSelectInput v-else-if="need === RuleNeed.STATES" v-model="statesModel" :options="stateOptions" inline
                      role="group" :aria-label="t('memberRules.valueStates')"/>
    <DecimalInput v-else-if="need === RuleNeed.NUMBER" class="w-28" :aria-label="t('memberRules.valueNumber')" :model-value="current.number ?? undefined"
                  @update:model-value="number => set({number: number ?? null})"/>
    <template v-else-if="need === RuleNeed.NUMBER_RANGE">
      <DecimalInput class="w-28" :aria-label="t('memberRules.valueFrom')" :model-value="current.number ?? undefined"
                    @update:model-value="number => set({number: number ?? null})"/>
      <span>{{ t('memberRules.and') }}</span>
      <DecimalInput class="w-28" :aria-label="t('memberRules.valueTo')" :model-value="current.numberTo ?? undefined"
                    @update:model-value="numberTo => set({numberTo: numberTo ?? null})"/>
    </template>
    <TextInput v-else-if="need === RuleNeed.TEXT" class="w-48" :aria-label="t('memberRules.valueText')" :model-value="current.text ?? ''"
               @update:model-value="text => set({text})"/>
    <DateInput v-else-if="need === RuleNeed.DATE" :aria-label="t('memberRules.valueDate')" :model-value="current.date ?? ''"
               @update:model-value="date => set({date: date || null})"/>
    <template v-else-if="need === RuleNeed.DURATION">
      <NumberInput class="w-20" :aria-label="t('memberRules.valueAmount')" :model-value="current.amount ?? undefined"
                   @update:model-value="amount => set({amount: amount ?? null, unit: unitModel})"/>
      <SelectInput v-model="unitModel" :aria-label="t('memberRules.valueUnit')">
        <option v-for="unit in units" :key="unit" :value="unit">{{ t(`memberRules.units.${unit}`) }}</option>
      </SelectInput>
    </template>
    <template v-else-if="need === RuleNeed.YEARS">
      <NumberInput class="w-20" :aria-label="t('memberRules.valueAmount')" :model-value="current.amount ?? undefined"
                   @update:model-value="amount => set({amount: amount ?? null, unit: RuleUnit.YEARS})"/>
      <span>{{ t('memberRules.years') }}</span>
    </template>
    <template v-else-if="need === RuleNeed.YEARS_RANGE">
      <NumberInput class="w-20" :aria-label="t('memberRules.valueFrom')" :model-value="current.amount ?? undefined"
                   @update:model-value="amount => set({amount: amount ?? null, unit: RuleUnit.YEARS})"/>
      <span>{{ t('memberRules.and') }}</span>
      <NumberInput class="w-20" :aria-label="t('memberRules.valueTo')" :model-value="current.amountTo ?? undefined"
                   @update:model-value="amountTo => set({amountTo: amountTo ?? null, unit: RuleUnit.YEARS})"/>
      <span>{{ t('memberRules.years') }}</span>
    </template>
  </div>
</template>
