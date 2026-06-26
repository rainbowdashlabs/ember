/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {MemberCheckSummary} from '@/api/types'
import MemberName from '@/components/avatar/MemberName.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import MemberStatusBadge from './MemberStatusBadge.vue'
import MemberRowActions from './MemberRowActions.vue'
import {checkerName, formatDate, isLockedByMe, isLockedByOther} from './memberHelpers'

const props = defineProps<{
  member: MemberCheckSummary
  currentMemberId: number | undefined
}>()

const emit = defineEmits<{
  (e: 'start-check', memberId: number): void
  (e: 'view-last-check', member: MemberCheckSummary): void
}>()

const {t} = useI18n()
</script>

<template>
  <TRow>
    <Td class="font-medium"><MemberName :identity="member.identity"/></Td>
    <Td muted>{{ formatDate(member.lastCheckedAt, t('inventory.check.neverChecked')) }}</Td>
    <Td muted>{{ checkerName(member) }}</Td>
    <Td>
      <MemberStatusBadge :member="member" :current-member-id="currentMemberId"/>
    </Td>
    <Td align="right">
      <MemberRowActions
          :member="member"
          :locked-by-me="isLockedByMe(member, currentMemberId)"
          :locked-by-other="isLockedByOther(member, currentMemberId)"
          @start-check="emit('start-check', $event)"
          @view-last-check="emit('view-last-check', $event)"
      />
    </Td>
  </TRow>
</template>
