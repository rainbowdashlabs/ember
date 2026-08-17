/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'

/** What an import reports back: how much of each kind it wrote, and anything it had to point out. */
export interface ImportResult {
  membersCreated: number
  managersCreated: number
  managersLinked: number
  groupsAssigned: number
  profileFieldsSet: number
  warnings: string[]
}

defineProps<{
  result: ImportResult
}>()

const emit = defineEmits<{
  startOver: []
  toList: []
}>()

const { t } = useI18n()
</script>

<template>
  <SuccessContainer class="space-y-4">
    <div class="flex items-center gap-2">
      <font-awesome-icon :icon="['fas', 'check-circle']" class="text-2xl text-success" />
      <SubHeader>{{ t('memberImport.doneTitle') }}</SubHeader>
    </div>
    <div class="grid grid-cols-2 sm:grid-cols-5 gap-3 text-center">
      <div>
        <p class="text-xl font-bold">{{ result.membersCreated }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberImport.membersCreated') }}</p>
      </div>
      <div>
        <p class="text-xl font-bold">{{ result.managersCreated }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberImport.managersCreated') }}</p>
      </div>
      <div>
        <p class="text-xl font-bold">{{ result.managersLinked }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberImport.managersLinked') }}</p>
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
    <Alert v-for="warning in result.warnings" :key="warning" variant="info">{{ warning }}</Alert>
    <div class="flex gap-3">
      <PrimaryButton :icon="['fas', 'users']" @click="emit('toList')">
        {{ t('memberImport.doneGoToList') }}
      </PrimaryButton>
      <SecondaryButton :icon="['fas', 'rotate']" @click="emit('startOver')">
        {{ t('memberImport.doneStartOver') }}
      </SecondaryButton>
    </div>
  </SuccessContainer>
</template>
