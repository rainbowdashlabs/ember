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
import ProfileFieldsLayout, {type LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
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

// The layout hands back the field it drew, and this form only ever draws the station's own, so the id
// is still all its caller needs
function onUpdate(field: LaidOutField, value: string) {
  emit('update', field.id, value)
}

function valueOf(field: LaidOutField): string {
  return props.getValue(field.id)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.title') }}</SectionHeader>

    <EmptyState compact v-if="editableFields.length === 0">{{ t('profile.noFields') }}</EmptyState>

    <ProfileFieldsLayout :fields="editableFields" :get-value="valueOf" @update="onUpdate"/>

    <SaveButton :action="saveAction"/>
  </NeutralContainer>
</template>
