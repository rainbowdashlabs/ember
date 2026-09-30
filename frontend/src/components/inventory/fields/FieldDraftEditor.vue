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
import ButtonRow from '@/components/button/ButtonRow.vue'
import {defaultFieldConfig, FieldType, type BooleanFieldConfig, type EnumFieldConfig, type FieldTypeName, type NumberFieldConfig, type TextFieldConfig} from '@/api/inventoryFields'
import type {DraftField} from './types'
import FieldDraftMetaForm from './FieldDraftMetaForm.vue'
import TextFieldConfigForm from './TextFieldConfigForm.vue'
import NumberFieldConfigForm from './NumberFieldConfigForm.vue'
import BooleanFieldConfigForm from './BooleanFieldConfigForm.vue'
import EnumFieldConfigForm from './EnumFieldConfigForm.vue'

const props = defineProps<{
    submitting: boolean
}>()

const draft = defineModel<DraftField>('draft', {required: true})

const emit = defineEmits<{
    cancel: []
    save: []
}>()

const {t} = useI18n()

function onTypeChanged(value: FieldTypeName) {
    if (draft.value.id) return
    draft.value = {...draft.value, fieldType: value, config: defaultFieldConfig(value)}
}
</script>

<template>
    <div class="mb-4 p-3 rounded-theme border border-(--bg-accent)">
        <SubHeader class="mb-2">
            {{ draft.id ? t('inventory.fields.edit') : t('inventory.fields.add') }}
        </SubHeader>
        <FieldDraftMetaForm v-model:draft="draft" @type-changed="onTypeChanged" />
        <TextFieldConfigForm
            v-if="draft.fieldType === FieldType.TEXT"
            v-model:config="draft.config as TextFieldConfig"
        />
        <NumberFieldConfigForm
            v-else-if="draft.fieldType === FieldType.NUMBER"
            :config="draft.config as NumberFieldConfig"
        />
        <BooleanFieldConfigForm
            v-else-if="draft.fieldType === FieldType.BOOLEAN"
            v-model:config="draft.config as BooleanFieldConfig"
        />
        <EnumFieldConfigForm
            v-else-if="draft.fieldType === FieldType.ENUM"
            v-model:config="draft.config as EnumFieldConfig"
        />
        <ButtonRow pair align="end" class="mt-3">
            <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="props.submitting" data-testid="field-save" @click="emit('save')">
                {{ props.submitting ? t('common.saving') : t('common.save') }}
            </PrimaryButton>
        </ButtonRow>
    </div>
</template>
