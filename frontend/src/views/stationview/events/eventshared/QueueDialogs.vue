/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import FullDateDialog from './FullDateDialog.vue'
import type {useQueueActions} from './useQueueActions'

/**
 * The questions deciding sign-ups may ask: accepting beyond the limit, and denying somebody who holds
 * a place. Closing the second one counts as no.
 */
const props = defineProps<{
  actions: Pick<ReturnType<typeof useQueueActions>, 'fullDateQuestion' | 'answerFullDate' | 'denyQuestion' | 'answerDeny'>
}>()

const {t} = useI18n()

const denyOpen = computed({
  get: () => props.actions.denyQuestion.value,
  set: shown => {
    if (!shown) props.actions.answerDeny(false)
  },
})
</script>

<template>
  <FullDateDialog :question="actions.fullDateQuestion.value" @answer="actions.answerFullDate"/>
  <ConfirmDeleteModal
      v-model="denyOpen"
      :title="t('registrationQueue.denyAcceptedTitle')"
      :message="t('registrationQueue.denyAcceptedBody')"
      :confirm-label="t('registrationQueue.denyAccepted')"
      :confirm-icon="['fas', 'xmark']"
      confirm-test-id="deny-accepted-confirm"
      @confirm="actions.answerDeny(true)"
  />
</template>
