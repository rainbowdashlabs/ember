/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ProfileFieldsLayout from '@/components/profilefields/ProfileFieldsLayout.vue'
import type {ProfileField} from '@/api/profileFields'

const props = defineProps<{
  editableFields: ProfileField[]
  getValue: (fieldId: number) => string
  saveAction: () => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'update', fieldId: number, value: string): void
}>()

const { t } = useI18n()

function onUpdate(fieldId: number, value: string) {
  emit('update', fieldId, value)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.title') }}</SectionHeader>

    <EmptyState compact v-if="editableFields.length === 0">{{ t('profile.noFields') }}</EmptyState>

    <ProfileFieldsLayout :fields="editableFields" :get-value="props.getValue" @update="onUpdate"/>

    <SaveButton :action="saveAction"/>
  </NeutralContainer>
</template>
