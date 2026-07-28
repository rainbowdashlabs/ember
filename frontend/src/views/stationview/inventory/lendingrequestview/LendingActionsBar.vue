/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {LendingStatus, type LendingRequestDetail} from '@/api/lending'

defineProps<{
  detail: LendingRequestDetail
}>()

const emit = defineEmits<{
  approve: []
  decline: []
  markReturned: []
  close: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap gap-2 mb-4">
    <template v-if="detail.request.isOwner">
      <SuccessButton :icon="['fas', 'check']" v-if="detail.request.request.status === LendingStatus.REQUESTED" @click="emit('approve')">
        {{ t('lending.approve') }}
      </SuccessButton>
      <ErrorButton :icon="['fas', 'xmark']" v-if="detail.request.request.status === LendingStatus.REQUESTED" @click="emit('decline')">
        {{ t('lending.decline') }}
      </ErrorButton>
    </template>
    <PrimaryButton v-if="detail.request.request.status === LendingStatus.LENT" @click="emit('markReturned')">
      {{ t('lending.markReturned') }}
    </PrimaryButton>
    <SecondaryButton v-if="detail.request.request.status === LendingStatus.RETURNED" @click="emit('close')">
      {{ t('lending.close') }}
    </SecondaryButton>
  </div>
</template>
