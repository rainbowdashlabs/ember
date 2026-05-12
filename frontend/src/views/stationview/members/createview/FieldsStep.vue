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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {ProfileField} from '@/api/types'

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

function parseConfig(configStr: string | undefined): Record<string, unknown> {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

const requiredFields = computed(() => props.fields.filter(f => parseConfig(f.config).required))
const optionalFields = computed(() => props.fields.filter(f => !parseConfig(f.config).required))

function getValue(fieldId: number): string {
  return props.values.get(fieldId) ?? ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepFields') }}</SectionHeader>

    <div v-if="fields.length === 0" class="text-center text-(--text-muted) py-4">
      {{ t('membersCreate.noFields') }}
    </div>

    <template v-if="requiredFields.length > 0">
      <h3 class="text-sm font-semibold uppercase text-(--text-muted)">{{ t('membersCreate.requiredFields') }}</h3>
      <div v-for="field in requiredFields" :key="field.id" class="space-y-1">
        <label class="block text-sm font-medium">{{ field.name }} <span class="text-error">*</span></label>
        <ProfileFieldInput
            :field-type="field.fieldType ?? 'text'"
            :model-value="getValue(field.id)"
            :options="(parseConfig(field.config).options as string[]) ?? []"
            @update:model-value="emit('setValue', field.id, $event)"
        />
      </div>
    </template>

    <template v-if="optionalFields.length > 0">
      <h3 class="text-sm font-semibold uppercase text-(--text-muted) pt-2">{{ t('membersCreate.optionalFields') }}</h3>
      <div v-for="field in optionalFields" :key="field.id" class="space-y-1">
        <label class="block text-sm font-medium">{{ field.name }}</label>
        <ProfileFieldInput
            :field-type="field.fieldType ?? 'text'"
            :model-value="getValue(field.id)"
            :options="(parseConfig(field.config).options as string[]) ?? []"
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
