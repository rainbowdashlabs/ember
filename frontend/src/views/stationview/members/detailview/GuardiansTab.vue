/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ManagerSection from './ManagerSection.vue'
import type { ProfileField, StationMember } from '@/api/types'

defineProps<{
  showManagerSection: boolean
  managers: StationMember[]
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
  <NeutralContainer v-else class="space-y-3">
    <MutedText tag="div" size="sm">{{ t('memberDetail.noGuardians') }}</MutedText>
  </NeutralContainer>
</template>
