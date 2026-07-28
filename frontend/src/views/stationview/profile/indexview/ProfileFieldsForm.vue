/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import type { ProfileField } from '@/api/profileFields'
import { parseFieldConfig } from '@/api/profileFields'

const props = defineProps<{
  editableFields: ProfileField[]
  getValue: (fieldId: number) => string
  saveAction: () => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'update', fieldId: number, value: string): void
}>()

const { t } = useI18n()

function onUpdate(fieldId: number, value: string) {
  emit('update', fieldId, value)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.title') }}</SectionHeader>

    <EmptyState compact v-if="editableFields.length === 0">{{ t('profile.noFields') }}</EmptyState>

    <div v-for="field in editableFields" :key="field.id" class="space-y-1">
      <FieldLabel>
        {{ field.name }}
        <span v-if="parseFieldConfig(field.config).required" class="text-error">*</span>
        <MutedText class="ml-1" v-if="parseFieldConfig(field.config).readonly">({{ t('profile.readonlyHint') }})</MutedText>
      </FieldLabel>

      <ProfileFieldInput
        :field-type="field.fieldType ?? 'TEXT'"
        :model-value="props.getValue(field.id)"
        :options="(parseFieldConfig(field.config).options as string[]) ?? []"
        :disabled="!!parseFieldConfig(field.config).readonly"
        @update:model-value="onUpdate(field.id, $event)"
      />
    </div>

    <SaveButton :action="saveAction"/>
  </NeutralContainer>
</template>
