/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {EnumFieldConfig, EnumOption} from '@/api/inventoryFields'
import {harmonizeKey} from './harmonize'

const props = defineProps<{
    config: EnumFieldConfig
}>()

const {t} = useI18n()

function onLabelInput(opt: EnumOption, label: string) {
    const follows = opt.value === '' || opt.value === harmonizeKey(opt.label)
    opt.label = label
    if (follows) {
        opt.value = harmonizeKey(label)
    }
}

function removeOption(idx: number) {
    props.config.options.splice(idx, 1)
}

function addOption() {
    props.config.options.push({value: '', label: ''})
}
</script>

<template>
    <div class="mt-3">
        <SubHeader class="mb-2">{{ t('inventory.fields.enum.options') }}</SubHeader>
        <ul class="flex flex-col gap-2">
            <li v-for="(opt, idx) in props.config.options" :key="idx" class="flex items-center gap-2">
                <TextInput :model-value="opt.label" :placeholder="t('inventory.fields.enum.label')" class="flex-1" @update:model-value="onLabelInput(opt, $event ?? '')" />
                <TextInput v-model="opt.value" :placeholder="t('inventory.fields.enum.value')" class="flex-1" />
                <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" @click="removeOption(idx)" />
            </li>
        </ul>
        <SecondaryButton compact class="mt-2" @click="addOption">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('inventory.fields.enum.add') }}
        </SecondaryButton>
    </div>
</template>
