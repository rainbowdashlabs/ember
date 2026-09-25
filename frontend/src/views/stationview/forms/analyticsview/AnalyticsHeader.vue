/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  title: string
  totalResponses: number
  /** Whether the reader may write the form as well as read its results. */
  canEdit: boolean
}>()

const emit = defineEmits<{
  export: []
  edit: []
  back: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between">
    <div>
      <SubHeader>{{ title }}</SubHeader>
      <p class="text-(--text-muted) text-sm">
        {{ t('forms.analytics.totalResponses') }}: {{ totalResponses }}
      </p>
    </div>
    <ButtonRow align="end">
      <SecondaryButton v-if="canEdit" :icon="['fas', 'pen']" @click="emit('edit')">
        {{ t('forms.edit') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'file-export']" @click="emit('export')">
        {{ t('forms.analytics.export') }}
      </SecondaryButton>
      <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    </ButtonRow>
  </div>
</template>
