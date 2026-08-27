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
import ProfileFieldsDisplay from '@/components/profilefields/ProfileFieldsDisplay.vue'
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
    <ProfileFieldsDisplay :fields="applicableFields" :get-value="field => getFieldValue(field.id)"/>
  </NeutralContainer>
  <ChangeHistory
    v-if="showChangeHistory"
    :member-id="memberId"
    :changes="changes"
    :current-member-id="currentMemberId"
    @reload="$emit('reload')"
  />
</template>
