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
import type {MergedField} from '@/api/generated/schema'

/**
 * The questions of a member's own profile. They come from the station and from its association, whose
 * questions are numbered on their own, so a value is always asked for and handed back by the whole
 * field rather than by its number.
 */
defineProps<{
  editableFields: MergedField[]
  getValue: (field: LaidOutField) => string
  saveAction: () => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'update', field: LaidOutField, value: string): void
}>()

const { t } = useI18n()

function onUpdate(field: LaidOutField, value: string) {
  emit('update', field, value)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.title') }}</SectionHeader>

    <EmptyState v-if="editableFields.length === 0" compact>{{ t('profile.noFields') }}</EmptyState>

    <ProfileFieldsLayout data-onboarding="profile.fields" :fields="editableFields" :get-value="getValue" @update="onUpdate"/>

    <SaveButton data-onboarding="profile.save" :action="saveAction"/>
  </NeutralContainer>
</template>
