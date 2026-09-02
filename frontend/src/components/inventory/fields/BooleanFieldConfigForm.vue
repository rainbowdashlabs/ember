/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import type {BooleanFieldConfig} from '@/api/inventoryFields'

const props = defineProps<{
    config: BooleanFieldConfig
}>()

const {t} = useI18n()
</script>

<template>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.boolean.trueLabel') }}</span>
            <TextInput v-model="props.config.trueLabel" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.boolean.falseLabel') }}</span>
            <TextInput v-model="props.config.falseLabel" />
        </label>
    </div>
</template>
