/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

const modelValue = defineModel<number | null>()

defineProps<{
    label: string
    min?: number
    max?: number
    placeholder?: string
}>()
</script>

<template>
    <div>
        <FieldLabel hint class="mb-1">{{ label }}</FieldLabel>
        <NumberInput
            :model-value="modelValue ?? undefined"
            :min="min"
            :max="max"
            :placeholder="placeholder"
            @update:model-value="modelValue = $event || null"
        />
    </div>
</template>
