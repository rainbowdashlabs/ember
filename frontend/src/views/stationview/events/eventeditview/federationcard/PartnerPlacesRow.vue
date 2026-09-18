/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * What one partner station may do with this appointment.
 *
 * <p>One choice with an optional number, not two switches. Handing somebody five places and then
 * choosing their five for them is not a thing anybody wants, so a number implies they decide, and
 * switching the deciding off clears the number with it.
 */
const props = defineProps<{
  partnerName: string
  disabled: boolean
}>()

const decides = defineModel<boolean>('decides', {required: true})
const budget = defineModel<number | null>('budget', {required: true})

const {t} = useI18n()

/** Whether a cap is being kept at all, which is what the number field is for. */
const capped = computed({
  get: () => budget.value !== null,
  set: (on: boolean) => {
    budget.value = on ? (budget.value ?? 1) : null
    if (on) decides.value = true
  },
})

/**
 * The number as the field speaks it. A cap of none is null here and absent there, and an emptied
 * field means none rather than zero: nobody clearing a box meant to give a partner no places at all.
 */
const budgetField = computed({
  get: () => budget.value ?? undefined,
  set: (value: number | undefined) => { budget.value = value ?? null },
})

const summary = computed(() => {
  if (!decides.value) return t('events.partnerPlacesHostDecides')
  if (budget.value === null) return t('events.partnerPlacesPartnerDecides')
  return t('events.partnerPlacesCapped', {count: budget.value})
})

function onDecidesChanged(on: boolean) {
  decides.value = on
  if (!on) budget.value = null
}
</script>

<template>
  <div class="space-y-2 border-t border-(--border) pt-2 first:border-0 first:pt-0">
    <div class="flex items-center justify-between gap-3 flex-wrap">
      <span class="text-sm font-medium">{{ props.partnerName }}</span>
      <MutedText size="sm">{{ summary }}</MutedText>
    </div>

    <div class="flex items-center gap-2 flex-wrap">
      <ToggleInput
          :model-value="decides"
          :disabled="props.disabled"
          :label="t('events.partnerPlacesLetPartnerDecide')"
          @update:model-value="onDecidesChanged"
      />
    </div>

    <div v-if="decides" class="flex items-center gap-2 flex-wrap">
      <ToggleInput v-model="capped" :disabled="props.disabled" :label="t('events.partnerPlacesCap')"/>
      <NumberInput
          v-if="capped"
          v-model="budgetField"
          :disabled="props.disabled"
          :min="0"
          class="w-24"
          data-testid="partner-place-budget"
      />
    </div>
  </div>
</template>
