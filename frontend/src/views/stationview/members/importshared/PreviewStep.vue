/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
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
  /** What a question is called, since the rows carry its identifier rather than its name. */
  fieldLabel: (fieldId: string) => string
}>()

const emit = defineEmits<{
  /** Strike a row out or put it back, by where it came from in the file. */
  toggleRow: [row: number]
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
        <Th></Th>
      </template>
      <TRow
          v-for="(member, index) in preview.members"
          :key="index"
          :class="member.ignored ? 'opacity-40 line-through' : ''"
          data-testid="preview-row"
      >
        <Td class="font-medium">{{ member.firstName }} {{ member.lastName }}</Td>
        <Td class="text-xs">{{ member.email }}</Td>
        <Td>{{ member.group }}</Td>
        <Td class="text-xs">
          <span v-for="(value, key) in member.profileFields" :key="String(key)" class="inline-block mr-2">
            <MutedText size="base">{{ fieldLabel(String(key)) }}:</MutedText> {{ value }}
          </span>
        </Td>
        <Td v-if="showContacts" class="text-xs">
          <div v-for="contact in member.contacts" :key="contact.name">
            {{ contact.name }}
            <MutedText v-if="contact.email" size="base">({{ contact.email }})</MutedText>
          </div>
        </Td>
        <Td align="right">
          <SecondaryButton class="!py-1 !px-2 !text-xs" data-testid="toggle-row" @click="emit('toggleRow', member.row)">
            {{ member.ignored ? t('memberImport.rowInclude') : t('memberImport.rowIgnore') }}
          </SecondaryButton>
        </Td>
      </TRow>
    </DataTable>
  </NeutralContainer>
</template>
