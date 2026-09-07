/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {TextFieldConfig} from '@/api/inventoryFields'

const props = defineProps<{
    config: TextFieldConfig
}>()

const {t} = useI18n()
</script>

<template>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
        <label class="flex items-center gap-2 text-sm">
            <ToggleInput v-model="props.config.multiline" />
            <span>{{ t('inventory.fields.text.multiline') }}</span>
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.text.maxLength') }}</span>
            <NumberInput v-model="props.config.maxLength" />
        </label>
    </div>
</template>
