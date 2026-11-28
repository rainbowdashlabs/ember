/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import HintIcon from '@/components/typography/HintIcon.vue'

/**
 * One switch of a field's settings, with what it does behind a hint beside its name.
 *
 * <p>The explanation sits in the hint rather than in a line under the switch, so a dialog of a dozen
 * switches reads as a list of names. The switch names the hint as its description, so a screen reader
 * still reads it out with the switch.
 */
const on = defineModel<boolean>({required: true})

defineProps<{
  label: string
  /** What the switch does, where its name alone does not make clear. */
  hint?: string
  /** What the switch is found by in tests. */
  testId?: string
}>()

const labelId = useId()
const hintId = useId()
</script>

<template>
  <div class="flex items-center justify-between gap-3">
    <div class="flex items-center gap-1">
      <span :id="labelId" class="text-sm font-medium">{{ label }}</span>
      <HintIcon v-if="hint" :id="hintId" :text="hint"/>
    </div>
    <ToggleInput v-model="on" :aria-labelledby="labelId" :aria-describedby="hint ? hintId : undefined"
                 :data-testid="testId"/>
  </div>
</template>
