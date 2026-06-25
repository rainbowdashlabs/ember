/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'

export interface MemberPreview {
  firstName: string
  lastName: string
  email: string
  group: string
  profileFields: Record<string, string>
  contacts: unknown[]
}

export interface PreviewResult {
  members: MemberPreview[]
  warnings: string[]
}

defineProps<{
  preview: PreviewResult
  loading: boolean
}>()

defineEmits<{
  (e: 'back'): void
  (e: 'import'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Alert v-for="w in preview.warnings" :key="w" variant="info">{{ w }}</Alert>

  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('teamImport.previewTitle', { count: preview.members.length }) }}</SubHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('memberImport.name') }}</Th>
            <Th>{{ t('memberImport.email') }}</Th>
            <Th>{{ t('memberImport.group') }}</Th>
            <Th>{{ t('memberImport.fields') }}</Th>
          </THead>
        </thead>
        <tbody>
          <tr v-for="(m, i) in preview.members" :key="i">
            <Td class="!px-2 font-medium">{{ m.firstName }} {{ m.lastName }}</Td>
            <Td class="!px-2 text-xs">{{ m.email }}</Td>
            <Td class="!px-2">{{ m.group }}</Td>
            <Td class="!px-2 text-xs">
              <span v-for="(v, k) in m.profileFields" :key="k" class="inline-block mr-2">
                <span class="text-(--text-muted)">{{ k }}:</span> {{ v }}
              </span>
            </Td>
          </tr>
        </tbody>
      </table>
    </div>
  </NeutralContainer>

  <div class="flex justify-between">
    <SecondaryButton @click="$emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :icon="['fas', 'download']" :disabled="loading" @click="$emit('import')">
      {{ loading ? t('common.loading') : t('memberImport.import') }}
    </PrimaryButton>
  </div>
</template>
