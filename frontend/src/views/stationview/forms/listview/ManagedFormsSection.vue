/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ManagedFormRow from './ManagedFormRow.vue'
import type { Form } from '@/api/types'

defineProps<{
  forms: Form[]
  canCreatePolls: boolean
  titleKey?: string
  statusLabel: (status: string) => string
}>()

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'publish', form: Form): void
  (e: 'close', form: Form): void
  (e: 'edit', form: Form): void
  (e: 'analytics', form: Form): void
  (e: 'delete', form: Form): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t(titleKey ?? 'forms.title') }}</SectionHeader>
      <PrimaryButton v-if="canCreatePolls" :icon="['fas', 'plus']" @click="emit('create')">
        {{ t('forms.create') }}
      </PrimaryButton>
    </div>

    <EmptyState compact v-if="forms.length === 0">{{ t('forms.noForms') }}</EmptyState>

    <div class="space-y-2">
      <ManagedFormRow
        v-for="form in forms"
        :key="form.id"
        :form="form"
        :can-create-polls="canCreatePolls"
        :status-label="statusLabel"
        :publish-label="t('forms.publish')"
        :close-label="t('forms.close')"
        :edit-label="t('forms.edit')"
        :analytics-label="t('forms.viewAnalytics')"
        :delete-label="t('forms.delete')"
        @publish="emit('publish', $event)"
        @close="emit('close', $event)"
        @edit="emit('edit', $event)"
        @analytics="emit('analytics', $event)"
        @delete="emit('delete', $event)"
      />
    </div>
  </div>
</template>
