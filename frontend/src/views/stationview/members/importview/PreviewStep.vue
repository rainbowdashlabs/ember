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
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <th class="text-left py-2 px-2">{{ t('memberImport.name') }}</th>
            <th class="text-left py-2 px-2">{{ t('memberImport.email') }}</th>
            <th class="text-left py-2 px-2">{{ t('memberImport.group') }}</th>
            <th class="text-left py-2 px-2">{{ t('memberImport.fields') }}</th>
            <th class="text-left py-2 px-2">{{ t('memberImport.contacts') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, i) in preview.members" :key="i" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="py-2 px-2 font-medium">{{ m.firstName }} {{ m.lastName }}</td>
            <td class="py-2 px-2 text-xs">{{ m.email }}</td>
            <td class="py-2 px-2">{{ m.group }}</td>
            <td class="py-2 px-2 text-xs">
              <span v-for="(v, k) in m.profileFields" :key="String(k)" class="inline-block mr-2">
                <span class="text-(--text-muted)">{{ k }}:</span> {{ v }}
              </span>
            </td>
            <td class="py-2 px-2 text-xs">
              <div v-for="c in m.contacts" :key="c.name">
                {{ c.name }} <span v-if="c.email" class="text-(--text-muted)">({{ c.email }})</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </NeutralContainer>

  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :disabled="loading" @click="emit('import')">
      <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
      {{ loading ? t('common.loading') : t('memberImport.import') }}
    </PrimaryButton>
  </div>
</template>
