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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TableHeaderCell from '@/components/typography/TableHeaderCell.vue'
import type {MemberStatus} from '@/api/twoFactorAdmin'

const props = defineProps<{
  members: MemberStatus[]
  userTypeLabel: (name: string) => string
}>()

const emit = defineEmits<{
  (e: 'reset', member: MemberStatus): void
}>()

const {t} = useI18n()

const enrolledCount = computed(() => props.members.filter(m => m.enrolled).length)
const mandatedCount = computed(() => props.members.filter(m => m.mandated).length)
const mandatedNotEnrolled = computed(() => props.members.filter(m => m.mandated && !m.enrolled).length)
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
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-(--text-muted)">
            <TableHeaderCell>{{ t('twoFactor.admin.col.name') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.col.email') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.col.userType') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.col.status') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.col.actions') }}</TableHeaderCell>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in props.members" :key="m.memberId" class="border-t border-(--border)">
            <td class="py-2 pr-3">{{ m.firstName }} {{ m.lastName }}</td>
            <td class="py-2 pr-3 text-(--text-muted)">{{ m.email }}</td>
            <td class="py-2 pr-3">{{ props.userTypeLabel(m.userType) }}</td>
            <td class="py-2 pr-3">
              <SuccessBadge v-if="m.enrolled">{{ t('twoFactor.admin.statusEnrolled') }}</SuccessBadge>
              <ErrorBadge v-else-if="m.mandated">{{ t('twoFactor.admin.statusMandatedGap') }}</ErrorBadge>
              <InfoBadge v-else>{{ t('twoFactor.admin.statusOptional') }}</InfoBadge>
            </td>
            <td class="py-2 pr-3">
              <ErrorButton v-if="m.enrolled" size="sm" @click="emit('reset', m)">
                {{ t('twoFactor.admin.reset') }}
              </ErrorButton>
            </td>
          </tr>
          <tr v-if="props.members.length === 0">
            <td colspan="5" class="py-4 text-(--text-muted) text-center">{{ t('twoFactor.admin.noMembers') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </NeutralContainer>
</template>
