/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import {isSection, spanClass} from './fieldLayout'

/**
 * The fields of a member, laid out the way the station arranged them: headings between them, and
 * the short ones beside each other rather than each on a row of its own.
 *
 * <p>The one place that decides this, so a field looks the same wherever it is filled in.
 */
const props = defineProps<{
  fields: ProfileField[]
  getValue: (fieldId: number) => string
  /** Whether the reader may write to fields the station marked read-only. */
  canEditReadonly?: boolean
}>()

const emit = defineEmits<{
  update: [fieldId: number, value: string]
}>()

const {t} = useI18n()

function locked(field: ProfileField): boolean {
  return !props.canEditReadonly && !!parseFieldConfig(field.config).readonly
}
</script>

<template>
  <div class="grid grid-cols-6 gap-x-4 gap-y-3 items-start">
    <template v-for="field in props.fields" :key="field.id">
      <div v-if="isSection(field)" :class="spanClass(field)" class="pt-2 first:pt-0">
        <SubHeader class="text-sm">{{ field.name }}</SubHeader>
      </div>
      <div v-else :class="spanClass(field)" class="space-y-1">
        <FieldLabel>
          {{ field.name }}
          <span v-if="parseFieldConfig(field.config).required" class="text-error">*</span>
          <MutedText v-if="locked(field)" class="ml-1">({{ t('profile.readonlyHint') }})</MutedText>
        </FieldLabel>
        <ProfileFieldInput
            :field-type="field.fieldType ?? 'TEXT'"
            :model-value="props.getValue(field.id)"
            :options="(parseFieldConfig(field.config).options as string[]) ?? []"
            :disabled="locked(field)"
            @update:model-value="emit('update', field.id, $event)"
        />
      </div>
    </template>
  </div>
</template>
