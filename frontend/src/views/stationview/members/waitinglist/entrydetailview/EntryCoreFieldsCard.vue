/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EntryGuardiansEditor from './EntryGuardiansEditor.vue'
import type { GuardianInput } from '@/api/types'

const firstname = defineModel<string>('firstname', { required: true })
const lastname = defineModel<string>('lastname', { required: true })
const notes = defineModel<string>('notes', { required: true })

defineProps<{
  guardians: GuardianInput[]
}>()

const emit = defineEmits<{
  'add-guardian': []
  'remove-guardian': [index: number]
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('waitingList.entryDetails') }}</SubHeader>
    <div class="grid gap-4 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.firstname') }}</FieldLabel>
        <TextInput v-model="firstname" />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
        <TextInput v-model="lastname" />
      </div>
    </div>
    <EntryGuardiansEditor
      :guardians="guardians"
      @add="emit('add-guardian')"
      @remove="(i: number) => emit('remove-guardian', i)"
    />
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
      <TextAreaInput v-model="notes" />
    </div>
  </NeutralContainer>
</template>
