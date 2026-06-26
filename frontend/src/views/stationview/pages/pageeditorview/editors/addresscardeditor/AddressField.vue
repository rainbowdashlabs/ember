/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const modelValue = defineModel<string>({required: true})

defineProps<{
    label: string
}>()
</script>

<template>
    <div>
        <FieldLabel hint class="mb-1">{{ label }}</FieldLabel>
        <TextInput v-model="modelValue"/>
    </div>
</template>
