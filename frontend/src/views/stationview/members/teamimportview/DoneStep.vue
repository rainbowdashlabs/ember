/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'

export interface TeamImportResult {
  membersCreated: number
  groupsAssigned: number
  profileFieldsSet: number
  warnings: string[]
}

defineProps<{
  result: TeamImportResult
}>()

defineEmits<{
  (e: 'start-over'): void
}>()

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <SuccessContainer class="space-y-3">
    <SubHeader>{{ t('memberImport.done') }}</SubHeader>
    <div class="grid grid-cols-3 gap-3 text-center">
      <div>
        <p class="text-xl font-bold">{{ result.membersCreated }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('teamImport.membersCreated') }}</p>
      </div>
      <div>
        <p class="text-xl font-bold">{{ result.groupsAssigned }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberImport.groupsAssigned') }}</p>
      </div>
      <div>
        <p class="text-xl font-bold">{{ result.profileFieldsSet }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberImport.fieldsSet') }}</p>
      </div>
    </div>
    <Alert v-for="w in result.warnings" :key="w" variant="info">{{ w }}</Alert>
    <div class="flex gap-3">
      <SecondaryButton @click="$emit('start-over')">{{ t('memberImport.importAnother') }}</SecondaryButton>
      <PrimaryButton @click="router.push({ name: 'members-list' })">{{ t('memberImport.toList') }}</PrimaryButton>
    </div>
  </SuccessContainer>
</template>
