/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FieldType} from '@/api/generated/schema'
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'

const props = defineProps<{
  /** False once another field of the station already is the birth date. */
  birthDateAvailable: boolean
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

/** A second date of birth is offered but not available: one per station is what makes it findable. */
const unavailable = computed<FieldType[]>(() => props.birthDateAvailable ? [] : [FieldType.BIRTH_DATE])
</script>

<template>
  <div class="space-y-4">
    <div v-if="props.named" class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldName') }}</FieldLabel>
      <TextInput v-model="name" data-testid="field-name" :placeholder="t('membersConfig.fieldNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('fieldTypes.type') }}</FieldLabel>
      <FieldTypePicker v-model="fieldType" :types="capabilities.types" :unavailable="unavailable"
                       data-testid="field-type"/>
    </div>
    <div v-if="props.named" class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldDescription') }}</FieldLabel>
      <TextInput
          v-model="description"
          data-testid="field-description"
          :placeholder="t('membersConfig.fieldDescriptionPlaceholder')"/>
      <MutedText class="text-xs">{{ t('membersConfig.fieldDescriptionHint') }}</MutedText>
    </div>
  </div>
</template>
