/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MemberLinkBadge from '@/components/accountlink/MemberLinkBadge.vue'
import {sendAgainTooSoon} from '@/components/accountlink/sendAgain'
import {accountLinks} from '@/api'
import {LinkStatus, type LinkState} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDate} from '@/util/format'
import {showToast} from '@/util/toast'

/**
 * Where the station's request to link an existing account to this member stands. The member has no
 * account until the person agrees, which is why the account actions are missing on this member while
 * the request waits. A request that waits or ran out can be sent again, a day after it was last sent;
 * one the person declined is their answer.
 */
const props = defineProps<{
  memberId: number
  canEdit: boolean
}>()

const {t} = useI18n()
const state = ref<LinkState | null>(null)
const sending = ref(false)
const sendFailure = ref<Failure | null>(null)

const {failure} = useAsyncLoader(async () => {
  state.value = await accountLinks.memberLink(props.memberId)
})

const shown = computed(() => state.value !== null && state.value.status !== LinkStatus.ACCEPTED)

const explanation = computed(() => {
  const current = state.value
  if (!current) return ''
  switch (current.status) {
    case LinkStatus.DECLINED:
      return t('memberLinks.declinedText', {date: formatDate(current.answeredAt)})
    case LinkStatus.EXPIRED:
      return t('memberLinks.expiredText', {date: formatDate(current.answeredAt ?? current.expiresAt)})
    default:
      return t('memberLinks.waitingText', {date: formatDate(current.sentAt), until: formatDate(current.expiresAt)})
  }
})

const sendAgainFrom = computed(() => state.value?.sendAgainFrom ?? null)
const tooSoon = computed(() => sendAgainTooSoon(sendAgainFrom.value))
const canSendAgain = computed(() => props.canEdit && sendAgainFrom.value !== null)

async function sendAgain() {
  sending.value = true
  sendFailure.value = null
  try {
    state.value = await accountLinks.sendAgain(props.memberId)
    showToast(t('memberLinks.sentAgain'), 'success')
  } catch (e) {
    sendFailure.value = describeFailure(e, t)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <FailureAlert :failure="failure ?? sendFailure"/>
  <NeutralContainer v-if="shown && state" class="space-y-3" data-testid="member-link">
    <div class="flex flex-wrap items-center gap-2">
      <SubHeader>{{ t('memberLinks.title') }}</SubHeader>
      <MemberLinkBadge :status="state.status"/>
    </div>
    <p class="text-sm">{{ explanation }}</p>
    <MutedText tag="p" size="sm">{{ t(`memberLinks.origin.${state.origin}`) }}</MutedText>
    <MutedText v-if="canSendAgain && tooSoon" tag="p" size="sm">
      {{ t('memberLinks.sendAgainFrom', {date: formatDate(sendAgainFrom)}) }}
    </MutedText>
    <ButtonRow v-if="canSendAgain">
      <SecondaryButton
          :disabled="sending || tooSoon"
          :icon="['fas', 'paper-plane']"
          data-testid="member-link-send-again"
          @click="sendAgain"
      >
        {{ t('memberLinks.sendAgain') }}
      </SecondaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
