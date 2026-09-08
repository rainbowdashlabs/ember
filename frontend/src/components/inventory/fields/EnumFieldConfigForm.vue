/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import type {EnumFieldConfig, EnumOption} from '@/api/inventoryFields'
import {harmonizeKey} from './harmonize'

/**
 * The answers a field of a piece of equipment offers.
 *
 * <p>The list is the one every choice question is written in. What is this feature's own is the
 * second half of an option: what is stored is not what is read, so that renaming "Jacke" to "Einsatz-
 * jacke" leaves the pieces already carrying it where they are. The stored half follows the label
 * while nobody has typed one of their own, and stops following the moment somebody does.
 */
const props = defineProps<{
    config: EnumFieldConfig
}>()

const {t} = useI18n()

function setOptions(options: EnumOption[]) {
    props.config.options.splice(0, props.config.options.length, ...options)
}

/** The label as somebody typed it, with the stored value following along while it still may. */
function relabel(option: EnumOption, label: string): EnumOption {
    const follows = option.value === '' || option.value === harmonizeKey(option.label)
    return {label, value: follows ? harmonizeKey(label) : option.value}
}

function setValue(option: EnumOption, value: string) {
    setOptions(props.config.options.map(candidate => (candidate === option ? {...candidate, value} : candidate)))
}
</script>

<template>
    <QuestionOptionsEditor
        :add-label="t('inventory.fields.enum.add')"
        :blank="() => ({value: '', label: ''})"
        :label="t('inventory.fields.enum.options')"
        :model-value="props.config.options"
        :text-of="(option: EnumOption) => option.label"
        :with-text="relabel"
        class="mt-3"
        @update:model-value="setOptions"
    >
        <template #after="{option}">
            <TextInput
                :model-value="option.value"
                :placeholder="t('inventory.fields.enum.value')"
                class="flex-1"
                @update:model-value="setValue(option, $event ?? '')"
            />
        </template>
    </QuestionOptionsEditor>
</template>
