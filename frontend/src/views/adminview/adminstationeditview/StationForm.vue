/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ManagerSection from './ManagerSection.vue'
import type {ManagerDetail} from '@/api/types'

defineProps<{
  manager: ManagerDetail | null
  editingManager: boolean
  isEdit: boolean
  save: () => Promise<void>
}>()

const name = defineModel<string>('name', {required: true})
const managerEmail = defineModel<string>('managerEmail', {required: true})

const emit = defineEmits<{
  'start-transfer': []
  'cancel-transfer': []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ isEdit ? t('adminStations.editTitle') : t('adminStations.createTitle') }}</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminStations.name') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('adminStations.namePlaceholder')"/>
    </div>

    <ManagerSection
      v-model:manager-email="managerEmail"
      :manager="manager"
      :editing-manager="editingManager"
      :is-edit="isEdit"
      @start-transfer="emit('start-transfer')"
      @cancel-transfer="emit('cancel-transfer')"
    />

    <SaveButton :disabled="!name" :action="save">
      {{ isEdit ? t('adminStations.save') : t('adminStations.createSubmit') }}
    </SaveButton>
  </NeutralContainer>
</template>
