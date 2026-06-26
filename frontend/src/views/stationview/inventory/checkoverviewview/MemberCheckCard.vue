/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {MemberCheckSummary} from '@/api/types'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberStatusBadge from './MemberStatusBadge.vue'
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
  <NeutralContainer class="space-y-2">
    <div class="flex items-center justify-between gap-2">
      <div class="font-medium truncate"><MemberName :identity="member.identity"/></div>
      <div>
        <MemberStatusBadge :member="member" :current-member-id="currentMemberId"/>
      </div>
    </div>
    <div class="text-xs text-(--text-muted)">
      {{ t('inventory.check.lastChecked') }}: {{ formatDate(member.lastCheckedAt, t('inventory.check.neverChecked')) }}
      <template v-if="member.checkerFirstName">
        &middot; {{ checkerName(member) }}
      </template>
    </div>
    <div class="flex gap-2">
      <SecondaryButton :icon="['fas', 'eye']" v-if="member.lastCheckedAt" class="text-sm flex-1" @click="emit('view-last-check', member)">
        {{ t('inventory.check.showLastCheck') }}
      </SecondaryButton>
      <SecondaryButton v-if="isLockedByMe(member, currentMemberId)" class="text-sm flex-1" @click="emit('start-check', member.memberId)">
        {{ t('inventory.check.continue') }}
      </SecondaryButton>
      <PrimaryButton v-else :disabled="isLockedByOther(member, currentMemberId)" class="text-sm flex-1"
                     @click="emit('start-check', member.memberId)">
        {{ t('inventory.check.start') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
