/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {FieldTypes} from '@/api/profileFields'
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'
import {FIELD_TYPE_ORDER, fieldTypeLabel} from '../fieldTypes'

const props = defineProps<{
  scope: string
  /** False once another field of the station already is the birth date. */
  birthDateAvailable: boolean
}>()

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})

const {t} = useI18n()

/**
 * What the owner of these fields may choose at all. A station may choose everything; an association
 * may not declare a date of birth, because the station declares its own and the two would collide.
 */
const capabilities = useFieldsCapabilities()

const availableOptions = computed(() => FIELD_TYPE_ORDER
    .filter(type => capabilities.types.includes(type))
    .filter((type) => {
      // What the field already is stays on the list whatever else rules it out. A select whose
      // value has no option shows nothing at all, which reads as a field with no type.
      if (type === fieldType.value) return true
      if (type === FieldTypes.AGE) return props.scope === 'MEMBER'
      if (type === FieldTypes.BIRTH_DATE) return props.birthDateAvailable
      return true
    })
    .map(type => ({value: type, label: fieldTypeLabel(t, type)})))
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldName') }}</FieldLabel>
      <TextInput v-model="name" data-testid="field-name" :placeholder="t('membersConfig.fieldNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldType') }}</FieldLabel>
      <SelectInput v-model="fieldType" data-testid="field-type">
        <option v-for="ft in availableOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
      </SelectInput>
    </div>
  </div>
</template>
