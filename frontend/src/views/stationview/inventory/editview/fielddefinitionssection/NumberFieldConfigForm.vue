/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {NumberFieldConfig} from '@/api/inventoryFields'

const props = defineProps<{
    config: NumberFieldConfig
}>()

const {t} = useI18n()

const writable = props.config as {
    min: number | undefined
    max: number | undefined
    step: number | undefined
    unit: string
}
</script>

<template>
    <div class="grid grid-cols-1 md:grid-cols-4 gap-3 mt-3">
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.number.min') }}</span>
            <NumberInput v-model="writable.min" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.number.max') }}</span>
            <NumberInput v-model="writable.max" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.number.step') }}</span>
            <NumberInput v-model="writable.step" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.number.unit') }}</span>
            <TextInput v-model="writable.unit" />
        </label>
    </div>
</template>
