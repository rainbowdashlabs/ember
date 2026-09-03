/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import MemberCheckNotes from './MemberCheckNotes.vue'
import type {AttendanceStatus, MemberNotes} from '@/api/attendance'
import type {CheckRow} from './useCheckMode'
import type {MemberIdentity} from '@/api/types'

const {t} = useI18n()
const {isMobile} = useBreakpoint()

defineProps<{
  currentRow: CheckRow | null
  checkIndex: number
  totalUnchecked: number
  memberName: string
  memberIdentity?: MemberIdentity | null
  notes?: MemberNotes
  canMoveSwap?: boolean
  canSignOffFound?: boolean
}>()

const emit = defineEmits<{
  setStatus: [status: AttendanceStatus]
  skip: []
  end: []
  moveSwap: [exchangeId: number, nextStatus: string, replacementItemId: number | null]
  signOffFound: [itemId: number]
}>()
</script>

<template>
  <NeutralContainer v-if="currentRow" class="space-y-4">
    <SectionHeader>{{ t('attendanceSession.checkMode') }}</SectionHeader>
    <div class="text-center space-y-4 py-4">
      <p class="text-2xl font-bold">
        <MemberName :identity="memberIdentity" size="md"/>
      </p>
      <p class="text-sm text-(--text-muted)">{{ checkIndex + 1 }} / {{ totalUnchecked }}</p>
      <MemberCheckNotes
          :notes="notes"
          :can-move-swap="canMoveSwap"
          :can-sign-off-found="canSignOffFound"
          class="text-left inline-block"
          @move-swap="(exchangeId, nextStatus, replacementItemId) => emit('moveSwap', exchangeId, nextStatus, replacementItemId)"
          @sign-off-found="(itemId) => emit('signOffFound', itemId)"
      />
      <div class="flex flex-col sm:flex-row justify-center gap-3 sm:gap-4">
        <SuccessButton :icon="['fas', 'check']" :full-width="isMobile" @click="emit('setStatus', 'PRESENT')">
          {{ t('attendanceSession.present') }}
        </SuccessButton>
        <ErrorButton :icon="['fas', 'xmark']" :full-width="isMobile" @click="emit('setStatus', 'ABSENT')">
          {{ t('attendanceSession.absent') }}
        </ErrorButton>
        <InfoButton :icon="['fas', 'ban']" :full-width="isMobile" @click="emit('setStatus', 'DECLINED')">
          {{ t('attendanceSession.declined') }}
        </InfoButton>
      </div>
      <div class="flex justify-center gap-3 pt-2">
        <SecondaryButton @click="emit('skip')">{{ t('attendanceSession.skip') }}</SecondaryButton>
        <SecondaryButton @click="emit('end')">{{ t('attendanceSession.endCheck') }}</SecondaryButton>
      </div>
    </div>
  </NeutralContainer>

  <div v-if="!currentRow" class="text-center py-6">
    <p class="text-lg font-semibold text-success">{{ t('attendanceSession.allChecked') }}</p>
    <SecondaryButton class="mt-3" @click="emit('end')">{{ t('attendanceSession.endCheck') }}</SecondaryButton>
  </div>
</template>
