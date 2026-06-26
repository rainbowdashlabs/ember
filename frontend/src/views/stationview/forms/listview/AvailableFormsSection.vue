/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AvailableFormRow from './AvailableFormRow.vue'
import type { FormListEntry } from '@/api/types'

defineProps<{
  forms: FormListEntry[]
  showHeading: boolean
}>()

const emit = defineEmits<{
  (e: 'fill', form: FormListEntry): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SectionHeader v-if="showHeading" class="mt-6">{{ t('forms.fillForm') }}</SectionHeader>

    <EmptyState compact v-if="forms.length === 0">{{ t('forms.noAvailableForms') }}</EmptyState>

    <div class="space-y-2">
      <AvailableFormRow
        v-for="form in forms"
        :key="form.id"
        :form="form"
        :responses-label="t('forms.responses')"
        :fill-label="t('forms.fillForm')"
        :edit-response-label="t('forms.editResponse')"
        @fill="emit('fill', $event)"
      />
    </div>
  </div>
</template>
