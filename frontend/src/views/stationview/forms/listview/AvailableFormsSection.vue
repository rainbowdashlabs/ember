/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import AvailableFormRow from './AvailableFormRow.vue'
import type { FormListEntry, FormRespondent } from '@/api/generated/schema'

defineProps<{
  forms: FormListEntry[]
  showHeading: boolean
}>()

const emit = defineEmits<{
  (e: 'fill', form: FormListEntry, respondent: FormRespondent): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader v-if="showHeading" class="mt-6">{{ t('forms.fillForm') }}</SubHeader>

    <EmptyState v-if="forms.length === 0" compact>{{ t('forms.noAvailableForms') }}</EmptyState>

    <div class="space-y-2">
      <AvailableFormRow
        v-for="form in forms"
        :key="form.id"
        :form="form"
        @fill="(entry, respondent) => emit('fill', entry, respondent)"
      />
    </div>
  </div>
</template>
