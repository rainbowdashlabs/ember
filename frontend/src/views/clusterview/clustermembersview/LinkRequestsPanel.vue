/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MemberLinkBadge from '@/components/accountlink/MemberLinkBadge.vue'
import {sendAgainTooSoon} from '@/components/accountlink/sendAgain'
import {accountLinks} from '@/api'
import {LinkStatus, type AssociationLinkState} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDate} from '@/util/format'
import {showToast} from '@/util/toast'

/**
 * The association's requests to addresses that already have an account: waiting for the person,
 * declined by them, or run out. Until the person accepts, the association has no member for the
 * account and reaches nothing of it. A request that waits or ran out can be sent again a day after it
 * last went out; one the person declined is their answer.
 */
const props = defineProps<{
  requests: readonly AssociationLinkState[]
  editable: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const {t} = useI18n()
const sending = ref<string | null>(null)
const failure = ref<Failure | null>(null)

function explanation(request: AssociationLinkState): string {
  switch (request.status) {
    case LinkStatus.DECLINED:
      return t('clusterMembers.requestDeclined', {date: formatDate(request.answeredAt)})
    case LinkStatus.EXPIRED:
      return t('clusterMembers.requestExpired', {date: formatDate(request.answeredAt ?? request.expiresAt)})
    default:
      return t('clusterMembers.requestWaiting', {date: formatDate(request.sentAt), until: formatDate(request.expiresAt)})
  }
}

async function sendAgain(uid: string) {
  sending.value = uid
  failure.value = null
  try {
    await accountLinks.sendAssociationRequestAgain(uid)
    showToast(t('memberLinks.sentAgain'), 'success')
    emit('changed')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    sending.value = null
  }
}
</script>

<template>
  <NeutralContainer v-if="props.requests.length > 0" class="space-y-4" data-testid="association-link-requests">
    <SectionHeader>{{ t('clusterMembers.requestsTitle') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('clusterMembers.requestsHint') }}</MutedText>
    <FailureAlert :failure="failure"/>
    <div
        v-for="request in props.requests"
        :key="request.uid"
        class="space-y-1 rounded-theme border border-(--border) px-3 py-2"
        data-testid="association-link-request"
    >
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-medium">{{ request.address }}</span>
        <SecondaryBadge>{{ t(`clusterOverview.role.${request.role}`) }}</SecondaryBadge>
        <MemberLinkBadge :status="request.status"/>
      </div>
      <MutedText tag="p" size="sm">{{ explanation(request) }}</MutedText>
      <div v-if="props.editable && request.sendAgainFrom" class="flex flex-wrap items-center gap-2">
        <SecondaryButton
            :disabled="sending === request.uid || sendAgainTooSoon(request.sendAgainFrom)"
            :icon="['fas', 'paper-plane']"
            data-testid="association-link-send-again"
            @click="sendAgain(request.uid)"
        >
          {{ t('memberLinks.sendAgain') }}
        </SecondaryButton>
        <MutedText v-if="sendAgainTooSoon(request.sendAgainFrom)" size="sm">
          {{ t('memberLinks.sendAgainFrom', {date: formatDate(request.sendAgainFrom)}) }}
        </MutedText>
      </div>
    </div>
  </NeutralContainer>
</template>
