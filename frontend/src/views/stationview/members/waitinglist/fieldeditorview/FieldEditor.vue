/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import QuestionCard from '@/components/input/questionsettings/QuestionCard.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import type {WaitingListFieldDraft} from './waitingListFieldDraft'

/**
 * One field of a waiting list, edited where it stands in the list.
 *
 * <p>The shared question card holds the name, the type and the settings every field has. What only
 * a waiting list's field has comes under it: whether the public form asks it.
 */
const field = defineModel<WaitingListFieldDraft>({required: true})

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
const toggleId = useId()

function update(patch: Partial<WaitingListFieldDraft>) {
  field.value = {...field.value, ...patch}
}
</script>

<template>
  <QuestionCard
      :field-type="field.fieldType"
      :name="field.name"
      :name-label="t('waitingList.fieldName')"
      :name-placeholder="t('waitingList.fieldNamePlaceholder')"
      :offers="['options', 'required']"
      :settings="field.settings"
      :types="OfferedFieldTypes.WAITING_LIST"
      @remove="emit('remove')"
      @update:field-type="fieldType => update({fieldType})"
      @update:name="name => update({name})"
      @update:settings="settings => update({settings})"
  >
    <label class="flex items-center gap-2 text-sm">
      <ToggleInput :model-value="field.isPublic" :aria-labelledby="toggleId"
                   @update:model-value="isPublic => update({isPublic})"/>
      <span :id="toggleId" class="font-medium">{{ t('waitingList.fieldPublic') }}</span>
    </label>
  </QuestionCard>
</template>
