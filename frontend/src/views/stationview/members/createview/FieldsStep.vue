/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ProfileField} from '@/api/profileFields'
import {parseFieldConfig} from '@/api/profileFields'

const {t} = useI18n()

const props = defineProps<{
  fields: ProfileField[]
  values: Map<number, string>
}>()

const emit = defineEmits<{
  next: []
  back: []
  setValue: [fieldId: number, value: string]
}>()

const requiredFields = computed(() => props.fields.filter(f => parseFieldConfig(f.config).required))
const optionalFields = computed(() => props.fields.filter(f => !parseFieldConfig(f.config).required))

function getValue(fieldId: number): string {
  return props.values.get(fieldId) ?? ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepFields') }}</SectionHeader>

    <EmptyState compact v-if="fields.length === 0">{{ t('membersCreate.noFields') }}</EmptyState>

    <template v-if="requiredFields.length > 0">
      <SubHeader class="text-sm font-semibold uppercase text-(--text-muted)">{{ t('membersCreate.requiredFields') }}</SubHeader>
      <div v-for="field in requiredFields" :key="field.id" class="space-y-1">
        <FieldLabel>{{ field.name }} <span class="text-error">*</span></FieldLabel>
        <ProfileFieldInput
            :field-type="field.fieldType ?? 'TEXT'"
            :model-value="getValue(field.id)"
            :options="(parseFieldConfig(field.config).options as string[]) ?? []"
            @update:model-value="emit('setValue', field.id, $event)"
        />
      </div>
    </template>

    <template v-if="optionalFields.length > 0">
      <SubHeader class="text-sm font-semibold uppercase text-(--text-muted) pt-2">{{ t('membersCreate.optionalFields') }}</SubHeader>
      <div v-for="field in optionalFields" :key="field.id" class="space-y-1">
        <FieldLabel>{{ field.name }}</FieldLabel>
        <ProfileFieldInput
            :field-type="field.fieldType ?? 'TEXT'"
            :model-value="getValue(field.id)"
            :options="(parseFieldConfig(field.config).options as string[]) ?? []"
            @update:model-value="emit('setValue', field.id, $event)"
        />
      </div>
    </template>

    <div class="flex justify-between">
      <SecondaryButton @click="emit('back')">{{ t('membersCreate.back') }}</SecondaryButton>
      <PrimaryButton @click="emit('next')">{{ t('membersCreate.next') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
