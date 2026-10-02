/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import QuestionSettingsEditor from '@/components/input/questionsettings/QuestionSettingsEditor.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { OfferedFieldTypes, type FieldTypeName } from '@/api/fieldTypes'
import type { QuestionSettingsModel } from '@/components/input/questionsettings/questionSettings'

const fieldName = defineModel<string>('fieldName', {required: true})
const fieldType = defineModel<FieldTypeName>('fieldType', {required: true})
const fieldSettings = defineModel<QuestionSettingsModel>('fieldSettings', {required: true})
const fieldPublic = defineModel<boolean>('fieldPublic', {required: true})

defineProps<{
  isEdit: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  save: []
  cancel: []
}>()

const { t } = useI18n()
const toggleId = useId()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ isEdit ? t('waitingList.editField') : t('waitingList.addField') }}</SubHeader>
    <LabelledField :label="t('waitingList.fieldName')">
      <TextInput v-model="fieldName" :placeholder="t('waitingList.fieldNamePlaceholder')" />
    </LabelledField>
    <LabelledField :label="t('waitingList.fieldType')">
      <FieldTypePicker v-model="fieldType" :types="OfferedFieldTypes.WAITING_LIST" />
    </LabelledField>
    <QuestionSettingsEditor v-model="fieldSettings" :field-type="fieldType" :offers="['options', 'required']" />
    <div class="flex items-center gap-2">
      <ToggleInput v-model="fieldPublic" :aria-labelledby="toggleId" />
      <span :id="toggleId" class="text-sm font-medium">{{ t('waitingList.fieldPublic') }}</span>
    </div>
    <ButtonRow pair align="end">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="saving || !fieldName.trim()" @click="emit('save')">
        {{ saving ? t('common.loading') : t('common.save') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
