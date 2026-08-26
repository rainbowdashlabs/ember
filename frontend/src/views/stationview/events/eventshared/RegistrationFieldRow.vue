/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {EventFieldTypes, type EventFieldTypeName, type EventRegistrationFieldDefinition} from '@/api/events'

const field = defineModel<EventRegistrationFieldDefinition>({required: true})

defineProps<{
  types: { value: EventFieldTypeName; label: string }[]
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

function update(patch: Partial<EventRegistrationFieldDefinition>) {
  field.value = {...field.value, ...patch}
}

function updateConfig(patch: Record<string, unknown>) {
  field.value = {...field.value, config: {...field.value.config, ...patch}}
}

function setOptions(text: string) {
  updateConfig({options: text.split('\n').map(o => o.trim()).filter(o => o !== '')})
}

function numberOrNull(value: unknown): number | null {
  return value === undefined || value === '' || value === null ? null : Number(value)
}
</script>

<template>
  <div class="border border-(--border) rounded-theme p-3 space-y-3">
    <div class="flex items-end gap-2">
      <div class="flex-1 min-w-0">
        <FieldLabel class="mb-1">{{ t('events.registrationFields.fieldName') }}</FieldLabel>
        <TextInput
            :model-value="field.name"
            :placeholder="t('events.registrationFields.fieldNamePlaceholder')"
            @update:model-value="v => update({name: String(v)})"
        />
      </div>
      <div class="w-44">
        <FieldLabel class="mb-1">{{ t('events.registrationFields.fieldType') }}</FieldLabel>
        <SelectInput
            :model-value="field.fieldType"
            @update:model-value="v => update({fieldType: String(v) as EventFieldTypeName})"
        >
          <option v-for="type in types" :key="type.value" :value="type.value">{{ type.label }}</option>
        </SelectInput>
      </div>
      <DeleteButton @click="emit('remove')"/>
    </div>

    <div class="flex flex-wrap gap-6">
      <div>
        <FieldLabel class="mb-1">{{ t('events.registrationFields.required') }}</FieldLabel>
        <ToggleInput
            :model-value="field.config.required ?? false"
            @update:model-value="v => updateConfig({required: v})"
        />
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('events.registrationFields.overview') }}</FieldLabel>
        <ToggleInput :model-value="field.overview" @update:model-value="v => update({overview: v})"/>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('events.registrationFields.managersOnly') }}</FieldLabel>
        <ToggleInput
            :model-value="field.config.managersOnly ?? false"
            @update:model-value="v => updateConfig({managersOnly: v})"
        />
      </div>
      <div class="flex-1 min-w-40">
        <FieldLabel class="mb-1">{{ t('events.registrationFields.defaultValue') }}</FieldLabel>
        <TextInput
            :model-value="field.config.defaultValue ?? ''"
            @update:model-value="v => updateConfig({defaultValue: String(v)})"
        />
        <FieldHint>{{ t('events.registrationFields.defaultValueHint') }}</FieldHint>
      </div>
    </div>

    <div v-if="field.fieldType === EventFieldTypes.NUMBER" class="flex gap-4">
      <div class="w-32">
        <FieldLabel class="mb-1">{{ t('events.registrationFields.min') }}</FieldLabel>
        <NumberInput
            :model-value="field.config.min ?? undefined"
            @update:model-value="v => updateConfig({min: numberOrNull(v)})"
        />
      </div>
      <div class="w-32">
        <FieldLabel class="mb-1">{{ t('events.registrationFields.max') }}</FieldLabel>
        <NumberInput
            :model-value="field.config.max ?? undefined"
            @update:model-value="v => updateConfig({max: numberOrNull(v)})"
        />
      </div>
    </div>

    <div v-if="field.fieldType === EventFieldTypes.ENUM">
      <FieldLabel class="mb-1">{{ t('events.registrationFields.options') }}</FieldLabel>
      <TextAreaInput
          :model-value="(field.config.options ?? []).join('\n')"
          class="font-mono text-sm"
          @update:model-value="v => setOptions(String(v))"
      />
      <FieldHint>{{ t('events.registrationFields.optionsHint') }}</FieldHint>
    </div>
  </div>
</template>
