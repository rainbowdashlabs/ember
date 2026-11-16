/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SliderInput from '@/components/input/number/SliderInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import {asFieldWidth, FIELD_WIDTHS, FieldWidths, ROW_COLUMNS, WIDTH_COLUMNS, type FieldWidthName} from './fieldLayout'
import {widthLabel} from './fieldWidths'

/**
 * How much of a row the field takes, which is what lets short fields stand beside each other.
 *
 * <p>A slider with one stop per width, narrowest to full, rather than a list: the widths are steps of
 * one scale, and the small row beside it shows how much of a row the chosen one takes. A value this
 * screen does not know stands at full, which is how such a field is drawn.
 */
const width = defineModel<string>({required: true})

const {t} = useI18n()
const id = useId()

const chosen = computed(() => asFieldWidth(width.value))

const stop = computed({
  get: () => FIELD_WIDTHS.indexOf(chosen.value),
  set: (index: number) => {
    width.value = FIELD_WIDTHS[index] ?? FieldWidths.FULL
  },
})

/** What a stop is called under the track; the last one says so in a word rather than as a fraction. */
function stopLabel(stopWidth: FieldWidthName): string {
  return stopWidth === FieldWidths.FULL ? t('membersConfig.widthSliderFull') : widthLabel(t, stopWidth)
}

const share = computed(() => `${(WIDTH_COLUMNS[chosen.value] / ROW_COLUMNS) * 100}%`)
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center gap-1">
      <FieldLabel :for="id">{{ t('membersConfig.fieldWidth') }}</FieldLabel>
      <HintIcon :text="t('membersConfig.fieldWidthHint')"/>
    </div>
    <div class="flex items-start gap-3" data-testid="field-width">
      <div class="min-w-0 flex-1">
        <SliderInput :id="id" v-model="stop" :min="0" :max="FIELD_WIDTHS.length - 1" :step="1"
                     :value-text="stopLabel(chosen)"/>
        <div class="mt-1 flex justify-between text-xs text-(--text-muted)" aria-hidden="true">
          <span v-for="stopWidth in FIELD_WIDTHS" :key="stopWidth" data-testid="field-width-stop"
                :class="stopWidth === chosen ? 'font-semibold text-(--text)' : ''">
            {{ stopLabel(stopWidth) }}
          </span>
        </div>
      </div>
      <div class="mt-0.5 h-3 w-12 shrink-0 overflow-hidden rounded-sm bg-bg-light-accent dark:bg-bg-dark-accent"
           aria-hidden="true" data-testid="field-width-row">
        <div class="h-full bg-primary" :style="{width: share}"/>
      </div>
    </div>
  </div>
</template>
