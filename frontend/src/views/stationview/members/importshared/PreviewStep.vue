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
import MutedText from '@/components/typography/MutedText.vue'
import DataTable from '@/components/table/DataTable.vue'
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
    <DataTable plain>
      <template #head>
        <Th>{{ t('memberImport.name') }}</Th>
        <Th>{{ t('memberImport.email') }}</Th>
        <Th>{{ t('memberImport.group') }}</Th>
        <Th>{{ t('memberImport.fields') }}</Th>
        <Th v-if="showContacts">{{ t('memberImport.contacts') }}</Th>
      </template>
      <TRow v-for="(member, index) in preview.members" :key="index">
        <Td class="font-medium">{{ member.firstName }} {{ member.lastName }}</Td>
        <Td class="text-xs">{{ member.email }}</Td>
        <Td>{{ member.group }}</Td>
        <Td class="text-xs">
          <span v-for="(value, key) in member.profileFields" :key="String(key)" class="inline-block mr-2">
            <MutedText size="base">{{ key }}:</MutedText> {{ value }}
          </span>
        </Td>
        <Td v-if="showContacts" class="text-xs">
          <div v-for="contact in member.contacts" :key="contact.name">
            {{ contact.name }}
            <MutedText v-if="contact.email" size="base">({{ contact.email }})</MutedText>
          </div>
        </Td>
      </TRow>
    </DataTable>
  </NeutralContainer>
</template>
