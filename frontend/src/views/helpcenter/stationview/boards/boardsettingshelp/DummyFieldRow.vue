/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DummyFieldTypeSelect from './DummyFieldTypeSelect.vue'

defineProps<{
    name: string
    type: string
    options?: string
    upDisabled?: boolean
    downDisabled?: boolean
}>()

const {t} = useI18n()
</script>

<template>
    <div class="border border-(--border) rounded-theme p-2">
        <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <TextInput :model-value="name" :placeholder="t('boards.fieldName')" class="flex-1" />
            <DummyFieldTypeSelect :model-value="type" />
            <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="upDisabled" />
            <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="downDisabled" />
            <IconButton :icon="['fas', 'xmark']" label="Remove" />
        </div>
        <div v-if="options" class="pl-6 mt-1">
            <TextInput :model-value="options" :placeholder="t('boards.fieldOptions')" class="text-sm" />
        </div>
    </div>
</template>
