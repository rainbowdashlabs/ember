/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

/**
 * One number an operator sets, with what it is called and what it means.
 *
 * <p>Every setting on this page is the same three lines, and six copies of them is what a page of
 * settings turns into if nobody stops it. Anything else given here, a test handle for one, reaches
 * the input rather than the box around it.
 */
defineOptions({inheritAttrs: false})

defineProps<{
  label: string
  hint: string
}>()

const value = defineModel<number>({required: true})
</script>

<template>
  <div>
    <FieldLabel class="mb-1">{{ label }}</FieldLabel>
    <NumberInput v-model="value" v-bind="$attrs"/>
    <MutedText class="mt-1" size="sm" tag="div">{{ hint }}</MutedText>
  </div>
</template>
