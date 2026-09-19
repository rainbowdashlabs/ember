/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import type {MemberStatus} from '@/api/twoFactorAdmin'
import {StationUserType} from '@/api/types'
import {useDataTable} from '@/composables/useDataTable'

/**
 * Who of the station has a second factor, with the ones it is required of but who have none
 * counted apart. The table sorts and filters by each column, the status among them.
 */
const props = defineProps<{
  members: MemberStatus[]
  userTypeLabel: (name: string) => string
}>()

const emit = defineEmits<{
  (e: 'reset', member: MemberStatus): void
}>()

const {t} = useI18n()

const TwoFactorStatus = {
  ENROLLED: 'ENROLLED',
  MANDATED_GAP: 'MANDATED_GAP',
  OPTIONAL: 'OPTIONAL',
} as const

/** Where a member stands: set up, required but missing, or free to go without. */
function twoFactorStatusOf(member: MemberStatus): string {
  if (member.enrolled) return TwoFactorStatus.ENROLLED
  return member.mandated ? TwoFactorStatus.MANDATED_GAP : TwoFactorStatus.OPTIONAL
}

const enrolledCount = computed(() => props.members.filter(m => m.enrolled).length)
const mandatedCount = computed(() => props.members.filter(m => m.mandated).length)
const mandatedNotEnrolled = computed(() =>
    props.members.filter(m => twoFactorStatusOf(m) === TwoFactorStatus.MANDATED_GAP).length)

const columns = computed<TableColumn<MemberStatus>[]>(() => [
  {key: 'name', label: t('twoFactor.admin.col.name'), type: ColumnTypes.TEXT, value: m => `${m.firstName} ${m.lastName}`.trim()},
  {key: 'email', label: t('twoFactor.admin.col.email'), type: ColumnTypes.TEXT, value: m => m.email},
  {
    key: 'userType',
    label: t('twoFactor.admin.col.userType'),
    type: ColumnTypes.ENUM,
    value: m => m.userType,
    options: Object.values(StationUserType).map(value => ({value, label: props.userTypeLabel(value)})),
  },
  {
    key: 'status',
    label: t('twoFactor.admin.col.status'),
    type: ColumnTypes.ENUM,
    value: twoFactorStatusOf,
    options: [
      {value: TwoFactorStatus.MANDATED_GAP, label: t('twoFactor.admin.statusMandatedGap')},
      {value: TwoFactorStatus.OPTIONAL, label: t('twoFactor.admin.statusOptional')},
      {value: TwoFactorStatus.ENROLLED, label: t('twoFactor.admin.statusEnrolled')},
    ],
  },
])

const table = useDataTable<MemberStatus>({
  id: 'station-two-factor-members',
  rows: () => props.members,
  columns,
  rowKey: m => m.memberId,
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.membersTitle') }}</SubHeader>
    <div class="flex flex-wrap gap-2 text-sm">
      <SuccessBadge>{{ t('twoFactor.admin.enrolledCount', {n: enrolledCount, total: props.members.length}) }}</SuccessBadge>
      <InfoBadge>{{ t('twoFactor.admin.mandatedCount', {n: mandatedCount}) }}</InfoBadge>
      <ErrorBadge v-if="mandatedNotEnrolled > 0">
        {{ t('twoFactor.admin.gapCount', {n: mandatedNotEnrolled}) }}
      </ErrorBadge>
    </div>
    <RecordTable :table="table" plain test-id="two-factor-members-table">
      <template #cell-email="{text}">
        <span class="text-(--text-muted)">{{ text }}</span>
      </template>
      <template #cell-status="{row, text}">
        <SuccessBadge v-if="row.enrolled">{{ text }}</SuccessBadge>
        <ErrorBadge v-else-if="row.mandated">{{ text }}</ErrorBadge>
        <InfoBadge v-else>{{ text }}</InfoBadge>
      </template>
      <template #actions-head>{{ t('twoFactor.admin.col.actions') }}</template>
      <template #actions="{row}">
        <ErrorButton v-if="row.enrolled" compact @click="emit('reset', row)">
          {{ t('twoFactor.admin.reset') }}
        </ErrorButton>
      </template>
      <template #empty>
        <MutedText tag="p" size="sm" class="text-center">{{ t('twoFactor.admin.noMembers') }}</MutedText>
      </template>
    </RecordTable>
  </NeutralContainer>
</template>
