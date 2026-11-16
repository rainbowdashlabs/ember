/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import LabelledField from '@/components/input/LabelledField.vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {FieldType} from '@/api/generated/schema'
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'

const props = defineProps<{
  /**
   * The types offered but not available, because another field of the owner already is the one field of
   * that type it may have: the date of birth, the gender.
   */
  unavailable: readonly FieldType[]
  /**
   * Whether this kind of entry is called anything. A spacer is a gap: nobody wants to think of a name
   * for one, and it is numbered where it is written down instead.
   */
  named: boolean
}>()

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<FieldType>('fieldType', {required: true})
const description = defineModel<string>('description', {required: true})

const {t} = useI18n()

/**
 * What the owner of these fields may choose at all. A station may choose everything; an association
 * may not declare a date of birth, because the station declares its own and the two would collide.
 */
const capabilities = useFieldsCapabilities()
</script>

<template>
  <div class="space-y-4">
    <div v-if="props.named" class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldName') }}</FieldLabel>
      <TextInput v-model="name" data-testid="field-name" :placeholder="t('membersConfig.fieldNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('fieldTypes.type') }}</FieldLabel>
      <FieldTypePicker v-model="fieldType" :types="capabilities.types" :unavailable="props.unavailable"
                       data-testid="field-type"/>
    </div>
    <LabelledField v-if="props.named" :label="t('membersConfig.fieldDescription')"
                   :help="t('membersConfig.fieldDescriptionHint')" help-as-hint>
      <template #default="{describedBy}">
        <TextInput
            v-model="description"
            data-testid="field-description"
            :aria-describedby="describedBy"
            :placeholder="t('membersConfig.fieldDescriptionPlaceholder')"/>
      </template>
    </LabelledField>
  </div>
</template>
