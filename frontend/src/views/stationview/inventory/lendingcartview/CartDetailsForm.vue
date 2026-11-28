/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {CartDetails} from '@/api/generated/schema'

/**
 * The days, the occasion and the note the whole cart asks with. A day is kept as soon as it is
 * picked, a text once its field is left.
 */
const props = defineProps<{
  details: Required<{[K in keyof CartDetails]: string}>
  disabled?: boolean
}>()

const emit = defineEmits<{
  change: [details: CartDetails]
}>()

const {t} = useI18n()
const ids = {from: useId(), to: useId(), occasion: useId(), note: useId()}
const draft = ref({...props.details})
watch(() => props.details, next => { draft.value = {...next} })

function save() {
  const next = draft.value
  if (Object.entries(next).every(([key, value]) => props.details[key as keyof CartDetails] === value)) return
  emit('change', {
    dateFrom: next.dateFrom || null,
    dateTo: next.dateTo || null,
    occasion: next.occasion,
    note: next.note,
  })
}
</script>

<template>
  <div class="space-y-3">
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel :for="ids.from">{{ t('lending.dateFrom') }}</FieldLabel>
        <DateInput :id="ids.from" v-model="draft.dateFrom" :disabled="disabled" data-testid="lending-cart-from"
                   @update:model-value="save"/>
      </div>
      <div class="space-y-1">
        <FieldLabel :for="ids.to">{{ t('lending.dateTo') }}</FieldLabel>
        <DateInput :id="ids.to" v-model="draft.dateTo" :min="draft.dateFrom || undefined" :disabled="disabled"
                   data-testid="lending-cart-to" @update:model-value="save"/>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel :for="ids.occasion">{{ t('lendingCartPage.occasion') }}</FieldLabel>
      <TextInput :id="ids.occasion" v-model="draft.occasion" :disabled="disabled" :placeholder="t('lendingCartPage.occasionPlaceholder')"
                 data-testid="lending-cart-occasion" @change="save"/>
    </div>
    <div class="space-y-1">
      <FieldLabel :for="ids.note">{{ t('lending.note') }}</FieldLabel>
      <TextAreaInput :id="ids.note" v-model="draft.note" :rows="3" :disabled="disabled" :placeholder="t('lending.notePlaceholder')"
                     data-testid="lending-cart-note" @change="save"/>
      <MutedText tag="p">{{ t('lendingCartPage.noteHint') }}</MutedText>
    </div>
  </div>
</template>
