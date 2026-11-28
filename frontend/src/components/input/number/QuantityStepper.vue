/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import BaseInput from '../BaseInput.vue'

/**
 * A count between a lowest and a highest value, typed or moved one at a time with a button either
 * side. A typed value counts once the field is left, pulled back into the range, so a count being
 * typed is never taken half-way.
 */
const model = defineModel<number>({required: true})

const props = withDefaults(defineProps<{
  min?: number
  max?: number
  disabled?: boolean
  /** What the count is of, for the field's accessible name. */
  label: string
  id?: string
}>(), {min: 1, max: Number.MAX_SAFE_INTEGER, id: undefined})

const {t} = useI18n()

function clamp(value: number): number {
  if (!Number.isFinite(value)) return props.min
  return Math.min(props.max, Math.max(props.min, Math.round(value)))
}

function settle(event: Event) {
  const field = event.target as HTMLInputElement
  const value = clamp(Number(field.value))
  field.value = String(value)
  model.value = value
}

function step(by: number) {
  model.value = clamp(model.value + by)
}
</script>

<template>
  <div class="inline-flex items-center gap-1" role="group" :aria-label="label">
    <IconButton :icon="['fas', 'minus']" :label="t('quantityStepper.less')" :disabled="disabled || model <= min" @click="step(-1)"/>
    <BaseInput :id="id" :model-value="String(model)" type="number" step="1" :min="min" :max="max" class="w-16 text-center"
               :disabled="disabled" :aria-label="label" @change="settle"/>
    <IconButton :icon="['fas', 'plus']" :label="t('quantityStepper.more')" :disabled="disabled || model >= max" @click="step(1)"/>
  </div>
</template>
