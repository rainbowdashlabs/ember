/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * One input with its label above it and, where there is one, a line underneath saying what it is for.
 *
 * <p>A form of eight fields is eight of these rather than eight hand-built wrappers, which is what keeps
 * the labels, the spacing and the hint text the same everywhere without anybody remembering to make them
 * so.
 *
 * <p>A dense screen can ask for the help behind a hint beside the label instead of under the field. The
 * slot is then handed the id of that hint as {@code describedBy}, for the input to name as its
 * description, so a screen reader still reads it with the input. An error always stays under the field.
 */
const props = defineProps<{
  label: string
  /** Whether the field may be left as it is, which the label marks. */
  hint?: boolean
  /** A line under the field, for the ones whose consequence is not obvious from the label. */
  help?: string
  /** Shows the help behind a hint beside the label rather than as a line under the field. */
  helpAsHint?: boolean
  /** What is wrong with what the field holds, shown under it in place of the help. */
  error?: string
}>()

const hintId = useId()
</script>

<template>
  <div class="space-y-1">
    <div v-if="props.helpAsHint && help" class="flex items-center gap-1">
      <FieldLabel :hint="hint">{{ label }}</FieldLabel>
      <HintIcon :id="hintId" :text="help"/>
    </div>
    <FieldLabel v-else :hint="hint">{{ label }}</FieldLabel>
    <slot v-bind="{describedBy: props.helpAsHint && help ? hintId : undefined}"/>
    <p v-if="error" class="text-sm text-error" role="alert">{{ error }}</p>
    <MutedText v-else-if="help && !props.helpAsHint" size="sm" tag="p">{{ help }}</MutedText>
  </div>
</template>
