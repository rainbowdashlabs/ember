/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/**
 * The "default value" part of a custom field definition, shared by the member profile fields and
 * the attendance session fields.
 *
 * Which editor is shown follows the field's type, because a default means something different per
 * type: for a date it is "today or nothing" rather than a fixed day that would go stale, and for a
 * boolean it is the toggle itself. Numbers are edited as numbers so the stored default stays a
 * number rather than a string that happens to contain digits.
 *
 * Labels are passed in rather than derived: the two settings pages word this section differently
 * and their translation keys are not parallel.
 */
defineProps<{
  fieldType: string
  enumOptions: string
  toggleLabel: string
  placeholder: string
  dateHint: string
  valueHint?: string
}>()

const hasDefault = defineModel<boolean>('hasDefault', {required: true})
const defaultValue = defineModel<string>('defaultValue', {required: true})
const defaultBool = defineModel<boolean>('defaultBool', {required: true})
const defaultToday = defineModel<boolean>('defaultToday', {required: true})
const defaultNumber = defineModel<number>('defaultNumber', {required: true})
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ toggleLabel }}</label>
      <ToggleInput v-model="hasDefault"/>
    </div>
    <template v-if="hasDefault">
      <template v-if="fieldType === 'BOOLEAN'">
        <ToggleInput v-model="defaultBool"/>
      </template>
      <template v-else-if="fieldType === 'DATE'">
        <ToggleInput v-model="defaultToday"/>
        <p class="text-xs text-(--text-muted)">{{ dateHint }}</p>
      </template>
      <template v-else-if="fieldType === 'NUMBER'">
        <NumberInput v-model="defaultNumber"/>
      </template>
      <template v-else-if="fieldType === 'ENUM'">
        <SelectInput v-model="defaultValue">
          <option value="">{{ placeholder }}</option>
          <option v-for="opt in enumOptions.split('\n').map(o => o.trim()).filter(o => o)" :key="opt"
                  :value="opt">{{ opt }}
          </option>
        </SelectInput>
      </template>
      <template v-else>
        <TextInput v-model="defaultValue" :placeholder="placeholder"/>
        <p v-if="valueHint" class="text-xs text-(--text-muted)">{{ valueHint }}</p>
      </template>
    </template>
  </div>
</template>
