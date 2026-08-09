/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { GuardianInput, WaitingListField } from '@/api/waitingList'
import BasicFieldsSection from './BasicFieldsSection.vue'
import GuardiansSection from './GuardiansSection.vue'
import CustomFieldsSection from './CustomFieldsSection.vue'

const props = defineProps<{
  firstname: string
  lastname: string
  guardians: GuardianInput[]
  notes: string
  fields: WaitingListField[]
  fieldValues: Record<number, string>
  parseConfig: (configStr: string | undefined | null) => Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:firstname', value: string): void
  (e: 'update:lastname', value: string): void
  (e: 'update:guardians', value: GuardianInput[]): void
  (e: 'update:notes', value: string): void
  (e: 'addGuardian'): void
  (e: 'removeGuardian', index: number): void
  (e: 'updateFieldValue', fieldId: number, value: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <BasicFieldsSection
      :firstname="firstname"
      :lastname="lastname"
      @update:firstname="emit('update:firstname', $event)"
      @update:lastname="emit('update:lastname', $event)"
    />

    <GuardiansSection
      :guardians="guardians"
      @update:guardians="emit('update:guardians', $event)"
      @add="emit('addGuardian')"
      @remove="(i) => emit('removeGuardian', i)"
    />

    <CustomFieldsSection
      :fields="fields"
      :values="fieldValues"
      :parse-config="parseConfig"
      @update="(id, v) => emit('updateFieldValue', id, v)"
    />

    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
      <TextAreaInput :model-value="notes" @update:model-value="emit('update:notes', $event ?? '')" :placeholder="t('waitingList.notesPlaceholder')"/>
    </div>
  </NeutralContainer>
</template>
