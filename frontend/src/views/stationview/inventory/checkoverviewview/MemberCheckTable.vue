/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import MemberName from '@/components/avatar/MemberName.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import MemberStatusBadge from './MemberStatusBadge.vue'
import MemberRowActions from './MemberRowActions.vue'
import {isLockedByMe, isLockedByOther} from './memberHelpers'
import {useMemberCheckTable} from './useMemberCheckTable'

/**
 * The members of the open tab, when each was last checked and by whom, with the button that starts
 * or continues a check. Every column sorts and filters from its header.
 */
const props = defineProps<{
  members: MemberCheckSummary[]
  currentMemberId: number | undefined
}>()

const emit = defineEmits<{
  (e: 'start-check', memberId: number): void
  (e: 'view-last-check', member: MemberCheckSummary): void
}>()

const {t} = useI18n()

const table = useMemberCheckTable(() => props.members, () => props.currentMemberId)
</script>

<template>
  <RecordTable :table="table" test-id="member-check-table">
    <template #cell-name="{row}">
      <span class="font-medium"><MemberName :identity="row.identity"/></span>
    </template>
    <template #cell-status="{row}">
      <MemberStatusBadge :current-member-id="props.currentMemberId" :member="row"/>
    </template>
    <template #actions="{row}">
      <MemberRowActions
          :locked-by-me="isLockedByMe(row, props.currentMemberId)"
          :locked-by-other="isLockedByOther(row, props.currentMemberId)"
          :member="row"
          @start-check="emit('start-check', $event)"
          @view-last-check="emit('view-last-check', $event)"
      />
    </template>
    <template #empty>
      <EmptyState>{{ t('inventory.check.noMembers') }}</EmptyState>
    </template>
  </RecordTable>
</template>
