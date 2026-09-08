/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import MemberName from '@/components/avatar/MemberName.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InlineDetail from '@/components/typography/InlineDetail.vue'
import ManagerSection from './ManagerSection.vue'
import type { ProfileField } from '@/api/profileFields'
import type { StationMember } from '@/api/types'

defineProps<{
  showManagerSection: boolean
  managers: StationMember[]
  /** Those this member looks after, which is the other half of the same relation. */
  managedMembers: StationMember[]
  availableManagers: StationMember[]
  managerValues: Map<number, Map<number, string>>
  managerUserTypesAsRoleMap: Map<number, string[]>
  fields: ProfileField[]
  canEdit: boolean
  memberDisplayName: (m: StationMember) => string
  getManagerFields: (id: number) => ProfileField[]
  getManagerFieldValue: (mgrId: number, fieldId: number) => unknown
}>()

defineEmits<{
  (e: 'link-manager', id: number): void
  (e: 'remove-manager', id: number): void
  (e: 'create-manager', data: { firstName: string; lastName: string; email: string }): void
}>()

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="space-y-6">
    <ManagerSection
      v-if="showManagerSection"
      :managers="managers"
      :available-managers="availableManagers"
      :manager-values="managerValues"
      :manager-roles="managerUserTypesAsRoleMap"
      :fields="fields"
      :readonly="!canEdit"
      :member-display-name-fn="memberDisplayName"
      :get-manager-fields-fn="getManagerFields"
      :get-manager-field-value-fn="getManagerFieldValue"
      @link-manager="$emit('link-manager', $event)"
      @remove-manager="$emit('remove-manager', $event)"
      @create-manager="$emit('create-manager', $event)"
      @edit-manager="(id) => router.push({ name: 'members-edit', params: { id } })"
    />

    <NeutralContainer v-if="managedMembers.length > 0" class="space-y-3">
      <SubHeader>{{ t('memberDetail.managedMembers') }}</SubHeader>
      <RouterLink
        v-for="managed in managedMembers"
        :key="managed.id"
        :to="{ name: 'members-detail', params: { id: managed.id } }"
        class="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent transition-colors"
        data-testid="managed-member-row"
      >
        <MemberName :identity="managed.identity">{{ memberDisplayName(managed) }}</MemberName>
        <InlineDetail v-if="managed.email">{{ managed.email }}</InlineDetail>
      </RouterLink>
    </NeutralContainer>

    <NeutralContainer v-if="!showManagerSection && managedMembers.length === 0" class="space-y-3">
      <MutedText tag="div" size="sm">{{ t('memberDetail.noRelations') }}</MutedText>
    </NeutralContainer>
  </div>
</template>
