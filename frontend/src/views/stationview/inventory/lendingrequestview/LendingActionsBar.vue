/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {LendingStatus, type LendingRequestDetail} from '@/api/generated/schema'

defineProps<{
  detail: LendingRequestDetail
}>()

const emit = defineEmits<{
  approve: []
  decline: []
  checkReturn: []
  remind: []
  close: []
}>()

const {t} = useI18n()

/** Whether the viewing station may take the step to this status now, as the server allows it. */
function may(detail: LendingRequestDetail, next: LendingStatus): boolean {
  return detail.request.allowedSteps.includes(next)
}
</script>

<template>
  <ButtonRow class="mb-4">
    <SuccessButton v-if="may(detail, LendingStatus.APPROVED)" :icon="['fas', 'check']" @click="emit('approve')">
      {{ t('lending.approve') }}
    </SuccessButton>
    <ErrorButton v-if="may(detail, LendingStatus.DECLINED)" :icon="['fas', 'xmark']" data-testid="lending-decline" @click="emit('decline')">
      {{ detail.request.isOwner ? t('lending.decline') : t('lending.withdraw') }}
    </ErrorButton>
    <PrimaryButton v-if="may(detail, LendingStatus.RETURNED)" :icon="['fas', 'clipboard-check']" data-testid="lending-start-return-check"
                   @click="emit('checkReturn')">
      {{ t('lendingReturn.start') }}
    </PrimaryButton>
    <SecondaryButton v-if="may(detail, LendingStatus.RETURNED) && detail.request.overdue" :icon="['fas', 'bell']" data-testid="lending-remind"
                     @click="emit('remind')">
      {{ t('lendingReturn.remind') }}
    </SecondaryButton>
    <SecondaryButton v-if="may(detail, LendingStatus.CLOSED)" @click="emit('close')">
      {{ t('lending.close') }}
    </SecondaryButton>
  </ButtonRow>
</template>
