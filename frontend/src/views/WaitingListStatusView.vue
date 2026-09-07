/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import StatusDetails from './waitingliststatusview/StatusDetails.vue'
import StatusActions from './waitingliststatusview/StatusActions.vue'
import InvitationDetails from './waitingliststatusview/InvitationDetails.vue'
import InvitationAnswerActions from './waitingliststatusview/InvitationAnswerActions.vue'
import RemoveConfirmationModal from './waitingliststatusview/RemoveConfirmationModal.vue'
import type { WaitingListAnswerName, WaitingListPublicStatus } from '@/api/waitingList'
import { waitingList } from '@/api'
import { useFlashMessage } from '@/composables/useFlashMessage'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useLinkAccessedResource } from '@/composables/useLinkAccessedResource'

const { t } = useI18n()

const {
  credential: token,
  data: status,
  loading,
  error: pageError,
  load: loadStatus,
} = useLinkAccessedResource<WaitingListPublicStatus>(
    'token',
    () => t('waitingList.publicStatus.noToken'),
    () => t('waitingList.publicStatus.invalidToken'),
    (t) => waitingList.getEntryStatus(t),
)

const { message: success, flash } = useFlashMessage(5000)

const showRemoveModal = ref(false)
const removed = ref(false)

const { running: confirming, error: confirmError, run: confirmInterest } = useAsyncAction(async () => {
  await waitingList.confirmInterest(token.value)
  status.value = await waitingList.getEntryStatus(token.value)
  flash(t('waitingList.publicStatus.confirmed'))
})

const { running: removing, error: removeError, run: removeFromList } = useAsyncAction(async () => {
  await waitingList.removeEntry(token.value)
  removed.value = true
  showRemoveModal.value = false
})

const answerNote = ref('')
const pendingAnswer = ref<WaitingListAnswerName | null>(null)

const { running: answering, error: answerError, run: sendAnswer } = useAsyncAction(async () => {
  if (!pendingAnswer.value) return
  await waitingList.answerInvitation(token.value, {
    eventId: status.value?.invitation?.eventId ?? null,
    date: status.value?.invitation?.date ?? null,
    answer: pendingAnswer.value,
    note: answerNote.value,
  })
  pendingAnswer.value = null
  answerNote.value = ''
  status.value = await waitingList.getEntryStatus(token.value)
  flash(t('waitingList.publicStatus.answered'))
})

function answer(chosen: WaitingListAnswerName) {
  pendingAnswer.value = chosen
  sendAnswer()
}

const error = computed(() => pageError.value || confirmError.value || removeError.value || answerError.value)

onMounted(loadStatus)
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-lg space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'clipboard-list']"/>
        <PageHeader class="text-2xl font-bold">{{ t('waitingList.publicStatus.title') }}</PageHeader>
      </div>

      <Spinner v-if="loading" size="md" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="removed">
        <Alert variant="info">{{ t('waitingList.publicStatus.removed') }}</Alert>
      </template>

      <template v-if="!loading && status && !removed">
        <StatusDetails :status="status" />
        <StatusActions
          v-if="status.status === 'WAITING'"
          :confirming="confirming"
          @confirm="confirmInterest"
          @remove="showRemoveModal = true"
        />
        <template v-if="status.status === 'INVITED'">
          <InvitationDetails v-if="status.invitation" :invitation="status.invitation" />
          <Alert v-if="status.answer" variant="info" data-testid="waitlist-answer-given">
            {{ t(`waitingList.publicStatus.answerGiven.${status.answer.answer}`) }}
          </Alert>
          <InvitationAnswerActions
            v-else
            v-model:note="answerNote"
            :answering="answering"
            @answer="answer"
          />
        </template>
      </template>

      <template v-if="!loading && !status && !removed">
        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{ t('waitingList.publicStatus.backToLogin') }}</router-link>
        </div>
      </template>

      <RemoveConfirmationModal
        v-model="showRemoveModal"
        :removing="removing"
        @confirm="removeFromList"
      />
    </div>
  </div>
</template>
