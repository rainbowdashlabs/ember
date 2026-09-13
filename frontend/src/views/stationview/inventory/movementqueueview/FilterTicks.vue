/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'

/**
 * One filter of the queue: a heading and the list of entries to tick under it.
 *
 * <p>The bar carries four of these and they differ only in what they are about, so they are one
 * component rather than four blocks that have to be kept looking alike by hand.
 */
const model = defineModel<string[]>({required: true})

const props = defineProps<{
  label: string
  options: {value: string; label: string}[]
  testid: string
}>()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ props.label }}</FieldLabel>
    <MultiSelectDropdown
        v-model="model"
        :data-testid="props.testid"
        :options="props.options"
        :placeholder="props.label"
    />
  </div>
</template>
