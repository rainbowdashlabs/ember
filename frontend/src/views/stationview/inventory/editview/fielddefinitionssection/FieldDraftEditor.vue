/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {FieldType, defaultFieldConfig} from '@/api/inventoryFields'
import type {
    BooleanFieldConfig,
    EnumFieldConfig,
    FieldTypeName,
    NumberFieldConfig,
    TextFieldConfig,
} from '@/api/inventoryFields'
import type {DraftField} from './types'
import FieldDraftMetaForm from './FieldDraftMetaForm.vue'
import TextFieldConfigForm from './TextFieldConfigForm.vue'
import NumberFieldConfigForm from './NumberFieldConfigForm.vue'
import BooleanFieldConfigForm from './BooleanFieldConfigForm.vue'
import EnumFieldConfigForm from './EnumFieldConfigForm.vue'

const props = defineProps<{
    draft: DraftField
    submitting: boolean
}>()

const emit = defineEmits<{
    (e: 'cancel'): void
    (e: 'save'): void
}>()

const {t} = useI18n()

function onTypeChanged(value: FieldTypeName) {
    if (props.draft.id) return
    props.draft.fieldType = value
    props.draft.config = defaultFieldConfig(value)
}
</script>

<template>
    <div class="mb-4 p-3 rounded-theme border border-(--bg-accent)">
        <SubHeader class="mb-2">
            {{ props.draft.id ? t('inventory.fields.edit') : t('inventory.fields.add') }}
        </SubHeader>
        <FieldDraftMetaForm :draft="props.draft" @type-changed="onTypeChanged" />
        <TextFieldConfigForm
            v-if="props.draft.fieldType === FieldType.TEXT"
            :config="props.draft.config as TextFieldConfig"
        />
        <NumberFieldConfigForm
            v-else-if="props.draft.fieldType === FieldType.NUMBER"
            :config="props.draft.config as NumberFieldConfig"
        />
        <BooleanFieldConfigForm
            v-else-if="props.draft.fieldType === FieldType.BOOLEAN"
            :config="props.draft.config as BooleanFieldConfig"
        />
        <EnumFieldConfigForm
            v-else-if="props.draft.fieldType === FieldType.ENUM"
            :config="props.draft.config as EnumFieldConfig"
        />
        <div class="flex justify-end gap-2 mt-3">
            <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="props.submitting" @click="emit('save')">
                {{ props.submitting ? t('common.saving') : t('common.save') }}
            </PrimaryButton>
        </div>
    </div>
</template>
