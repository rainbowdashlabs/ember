/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import type { PreviewResult } from './memberImport'

defineProps<{
  preview: PreviewResult
  title: string
  showContacts?: boolean
}>()

const { t } = useI18n()
</script>

<template>
  <Alert v-for="warning in preview.warnings" :key="warning" variant="info">{{ warning }}</Alert>

  <NeutralContainer class="space-y-3">
    <SubHeader>{{ title }}</SubHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('memberImport.name') }}</Th>
            <Th>{{ t('memberImport.email') }}</Th>
            <Th>{{ t('memberImport.group') }}</Th>
            <Th>{{ t('memberImport.fields') }}</Th>
            <Th v-if="showContacts">{{ t('memberImport.contacts') }}</Th>
          </THead>
        </thead>
        <tbody>
          <TRow v-for="(member, index) in preview.members" :key="index">
            <Td class="font-medium">{{ member.firstName }} {{ member.lastName }}</Td>
            <Td class="text-xs">{{ member.email }}</Td>
            <Td>{{ member.group }}</Td>
            <Td class="text-xs">
              <span v-for="(value, key) in member.profileFields" :key="String(key)" class="inline-block mr-2">
                <span class="text-(--text-muted)">{{ key }}:</span> {{ value }}
              </span>
            </Td>
            <Td v-if="showContacts" class="text-xs">
              <div v-for="contact in member.contacts" :key="contact.name">
                {{ contact.name }}
                <span v-if="contact.email" class="text-(--text-muted)">({{ contact.email }})</span>
              </div>
            </Td>
          </TRow>
        </tbody>
      </table>
    </div>
  </NeutralContainer>
</template>
