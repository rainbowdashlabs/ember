/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import ChangeHistory from './ChangeHistory.vue'
import type { ProfileFieldChange } from '@/api/profileFieldChanges'
import type { ProfileField } from '@/api/profileFields'

defineProps<{
  applicableFields: ProfileField[]
  memberId: number
  currentMemberId: number
  changes: ProfileFieldChange[]
  showChangeHistory: boolean
  getFieldValue: (fieldId: number) => unknown
}>()

defineEmits<{
  (e: 'reload'): void
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader class="text-sm">{{ t('memberDetail.fields') }}</SubHeader>
    <MutedText tag="div" size="sm" class="py-2" v-if="applicableFields.length === 0">
      {{ t('memberDetail.noFields') }}
    </MutedText>
    <div class="grid gap-2 sm:grid-cols-2">
      <div v-for="field in applicableFields" :key="field.id" class="text-sm">
        <span class="text-(--text-muted)">{{ field.name }}:</span>
        <span class="ml-1 font-medium"><FieldValueDisplay :value="getFieldValue(field.id)" :field-type="field.fieldType"/></span>
      </div>
    </div>
  </NeutralContainer>
  <ChangeHistory
    v-if="showChangeHistory"
    :member-id="memberId"
    :changes="changes"
    :current-member-id="currentMemberId"
    @reload="$emit('reload')"
  />
</template>
