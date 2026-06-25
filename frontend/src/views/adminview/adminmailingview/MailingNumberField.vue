/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  label: string
  hint?: string
}>()

const value = defineModel<number>({required: true})
</script>

<template>
  <div>
    <FieldLabel class="mb-1">{{ label }}</FieldLabel>
    <NumberInput v-model="value"/>
    <MutedText v-if="hint" tag="div" class="mt-1">{{ hint }}</MutedText>
  </div>
</template>
