/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import type {FieldType} from '@/api/generated/schema'
import type {DraftField} from './types'
import {harmonizeKey} from './harmonize'

const draft = defineModel<DraftField>('draft', {required: true})

const emit = defineEmits<{
    (e: 'type-changed', value: FieldType): void
}>()

const {t} = useI18n()

watch(() => draft.value.label, (label, previous) => {
    if (draft.value.id) return
    if (draft.value.key === '' || draft.value.key === harmonizeKey(previous ?? '')) {
        draft.value.key = harmonizeKey(label)
    }
})
</script>

<template>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.label') }}</span>
            <TextInput v-model="draft.label" data-testid="field-label" :placeholder="t('inventory.fields.labelPlaceholder')" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.key') }}</span>
            <TextInput v-model="draft.key" :disabled="!!draft.id" :placeholder="t('inventory.fields.keyPlaceholder')" />
        </label>
        <LabelledField :label="t('inventory.fields.type')">
            <FieldTypePicker
                :disabled="!!draft.id"
                :model-value="draft.fieldType"
                :types="OfferedFieldTypes.INVENTORY"
                data-testid="field-type"
                @update:model-value="value => emit('type-changed', value)"
            />
        </LabelledField>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.sortOrder') }}</span>
            <NumberInput v-model="draft.sortOrder" />
        </label>
        <label class="flex items-center gap-2 text-sm md:col-span-2">
            <ToggleInput v-model="draft.required" />
            <span>{{ t('inventory.fields.required') }}</span>
        </label>
    </div>
</template>
