/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  exportingGdpr: boolean
  exportingMemberId: number | null
  managedMembers: {id: number, name: string}[]
  deletingAccount: boolean
}>()

const emit = defineEmits<{
  exportOwn: []
  exportMember: [memberId: number]
  showDeleteModal: []
  restartTour: []
}>()

const {t} = useI18n()
</script>

<template>
  <!-- GDPR Data Export -->
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('userSettings.gdprTitle') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('userSettings.gdprHint') }}</p>

    <PrimaryButton :disabled="exportingGdpr" @click="emit('exportOwn')">
      <font-awesome-icon :icon="['fas', 'download']" class="mr-2"/>
      {{ exportingGdpr ? t('common.loading') : t('userSettings.gdprExport') }}
    </PrimaryButton>

    <template v-if="managedMembers.length > 0">
      <p class="text-sm font-medium pt-2">{{ t('userSettings.gdprManaged') }}</p>
      <div class="space-y-2">
        <div v-for="m in managedMembers" :key="m.id" class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-sm">{{ m.name }}</span>
          <SecondaryButton :disabled="exportingMemberId === m.id" @click="emit('exportMember', m.id)">
            <font-awesome-icon :icon="['fas', 'download']" class="mr-2"/>
            {{ exportingMemberId === m.id ? t('common.loading') : t('userSettings.gdprExportManaged', {name: m.name}) }}
          </SecondaryButton>
        </div>
      </div>
    </template>
  </NeutralContainer>

  <!-- Delete Account -->
  <ErrorContainer class="space-y-3">
    <SubHeader>{{ t('userSettings.deleteTitle') }}</SubHeader>
    <p class="text-sm">{{ t('userSettings.deleteWarning') }}</p>
    <ErrorButton :icon="['fas', 'trash']" @click="emit('showDeleteModal')">
      {{ t('userSettings.deleteAccount') }}
    </ErrorButton>
  </ErrorContainer>

  <!-- Restart Tour -->
  <NeutralContainer class="space-y-2">
    <SubHeader>{{ t('tour.restartButton') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('tour.restartHint') }}</p>
    <SecondaryButton :icon="['fas', 'rotate']" @click="emit('restartTour')">
      {{ t('tour.restartButton') }}
    </SecondaryButton>
  </NeutralContainer>
</template>
