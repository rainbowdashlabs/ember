/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const fieldName = defineModel<string>('fieldName', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})
const fieldRequired = defineModel<boolean>('fieldRequired', {required: true})
const fieldPublic = defineModel<boolean>('fieldPublic', {required: true})
const fieldEnumOptions = defineModel<string>('fieldEnumOptions', {required: true})

defineProps<{
  isEdit: boolean
  fieldTypes: readonly string[]
  fieldTypeLabel: (type: string) => string
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ isEdit ? t('waitingList.editField') : t('waitingList.addField') }}</SubHeader>
    <div class="space-y-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.fieldName') }}</FieldLabel>
        <TextInput v-model="fieldName" :placeholder="t('waitingList.fieldNamePlaceholder')" />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.fieldType') }}</FieldLabel>
        <SelectInput v-model="fieldType" class="w-full">
          <option v-for="ft in fieldTypes" :key="ft" :value="ft">{{ fieldTypeLabel(ft) }}</option>
        </SelectInput>
      </div>
      <div v-if="fieldType === 'ENUM'" class="space-y-1">
        <FieldLabel>{{ t('waitingList.enumOptions') }}</FieldLabel>
        <TextInput v-model="fieldEnumOptions" :placeholder="t('waitingList.enumOptionsPlaceholder')" />
        <p class="text-xs text-(--text-muted)">{{ t('waitingList.enumOptionsHint') }}</p>
      </div>
    </div>
    <div class="space-y-2">
      <div class="flex items-center gap-2">
        <ToggleInput v-model="fieldRequired" />
        <label class="text-sm font-medium">{{ t('waitingList.required') }}</label>
      </div>
      <div class="flex items-center gap-2">
        <ToggleInput v-model="fieldPublic" />
        <label class="text-sm font-medium">{{ t('waitingList.fieldPublic') }}</label>
      </div>
    </div>
    <div class="flex justify-end gap-2">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="saving || !fieldName.trim()" @click="emit('save')">
        {{ saving ? t('common.loading') : t('common.save') }}
      </PrimaryButton>
    </div>
  </div>
</template>
