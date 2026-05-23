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
import type {AttendanceEntry, AttendanceStatus} from '@/api/types'

const {t} = useI18n()
const {isMobile} = useBreakpoint()

defineProps<{
  currentEntry: AttendanceEntry | null
  checkIndex: number
  totalUnchecked: number
  memberName: string
}>()

const emit = defineEmits<{
  setStatus: [status: AttendanceStatus]
  skip: []
  end: []
}>()
</script>

<template>
  <NeutralContainer v-if="currentEntry" class="space-y-4">
    <SectionHeader>{{ t('attendanceSession.checkMode') }}</SectionHeader>
    <div class="text-center space-y-4 py-4">
      <p class="text-2xl font-bold">
        <MemberName :name="memberName" :member-id="currentEntry.memberId" size="md"/>
      </p>
      <p class="text-sm text-(--text-muted)">{{ checkIndex + 1 }} / {{ totalUnchecked }}</p>
      <div class="flex flex-col sm:flex-row justify-center gap-3 sm:gap-4">
        <SuccessButton :full-width="isMobile" @click="emit('setStatus', 'PRESENT')">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-2"/>
          {{ t('attendanceSession.present') }}
        </SuccessButton>
        <ErrorButton :full-width="isMobile" @click="emit('setStatus', 'ABSENT')">
          <font-awesome-icon :icon="['fas', 'xmark']" class="mr-2"/>
          {{ t('attendanceSession.absent') }}
        </ErrorButton>
        <InfoButton :full-width="isMobile" @click="emit('setStatus', 'DECLINED')">
          <font-awesome-icon :icon="['fas', 'ban']" class="mr-2"/>
          {{ t('attendanceSession.declined') }}
        </InfoButton>
      </div>
      <div class="flex justify-center gap-3 pt-2">
        <SecondaryButton @click="emit('skip')">{{ t('attendanceSession.skip') }}</SecondaryButton>
        <SecondaryButton @click="emit('end')">{{ t('attendanceSession.endCheck') }}</SecondaryButton>
      </div>
    </div>
  </NeutralContainer>

  <div v-if="!currentEntry" class="text-center py-6">
    <p class="text-lg font-semibold text-success">{{ t('attendanceSession.allChecked') }}</p>
    <SecondaryButton class="mt-3" @click="emit('end')">{{ t('attendanceSession.endCheck') }}</SecondaryButton>
  </div>
</template>
