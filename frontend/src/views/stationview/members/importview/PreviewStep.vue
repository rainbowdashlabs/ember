/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'

export interface ContactPreview {
  name: string
  phone: string
  email: string
}

export interface MemberPreview {
  firstName: string
  lastName: string
  email: string
  group: string
  profileFields: Record<string, string>
  contacts: ContactPreview[]
}

export interface PreviewResult {
  members: MemberPreview[]
  warnings: string[]
}

const { t } = useI18n()

defineProps<{
  preview: PreviewResult
  loading: boolean
}>()

const emit = defineEmits<{
  back: []
  import: []
}>()
</script>

<template>
  <Alert v-for="w in preview.warnings" :key="w" variant="info">{{ w }}</Alert>

  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('memberImport.previewTitle', { count: preview.members.length }) }}</SubHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('memberImport.name') }}</th>
            <Th>{{ t('memberImport.email') }}</th>
            <Th>{{ t('memberImport.group') }}</th>
            <Th>{{ t('memberImport.fields') }}</th>
            <Th>{{ t('memberImport.contacts') }}</th>
          </THead>
        </thead>
        <tbody>
          <TRow v-for="(m, i) in preview.members" :key="i">
            <td class="py-2 px-2 font-medium">{{ m.firstName }} {{ m.lastName }}</td>
            <Td class="!px-2 text-xs">{{ m.email }}</td>
            <td class="py-2 px-2">{{ m.group }}</Td>
            <Td class="!px-2 text-xs">
              <span v-for="(v, k) in m.profileFields" :key="String(k)" class="inline-block mr-2">
                <span class="text-(--text-muted)">{{ k }}:</span> {{ v }}
              </span>
            </Td>
            <Td class="!px-2 text-xs">
              <div v-for="c in m.contacts" :key="c.name">
                {{ c.name }} <span v-if="c.email" class="text-(--text-muted)">({{ c.email }})</span>
              </div>
            </Td>
          </TRow>
        </tbody>
      </table>
    </div>
  </NeutralContainer>

  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :icon="['fas', 'download']" :disabled="loading" @click="emit('import')">
      {{ loading ? t('common.loading') : t('memberImport.import') }}
    </PrimaryButton>
  </div>
</template>
