/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import PasskeyCodeDisplay from '@/components/passkey/PasskeyCodeDisplay.vue'
import {members, passkeys} from '@/api'
import type {MemberPasskeyCode} from '@/api/members'
import type {StationMember} from '@/api/types'
import type {PasskeyModeName} from '@/api/adminSettings'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The member manager's way back in for somebody who lost theirs: onboard again (passkeys gone,
 * sessions ended, a fresh setup link where mail about the account goes), and the passkey code
 * for an addressless member with no guardian at hand. Neither is a new power; whoever may press
 * these can reset a password today.
 */
const {t} = useI18n()

const props = defineProps<{member: StationMember}>()

const passkeyMode = ref<PasskeyModeName>('OFF')
const passkeyCode = ref<MemberPasskeyCode | null>(null)
const failure = ref<Failure | null>(null)
const notice = ref('')
const busy = ref(false)

const addressless = computed(() => !props.member.email || props.member.email.endsWith('.local'))

onMounted(() => {
  passkeys.publicPasskeyMode().then(mode => passkeyMode.value = mode).catch(() => {})
})

async function onboardAgain() {
  failure.value = null
  notice.value = ''
  busy.value = true
  try {
    const result = await members.onboardAgain(props.member.accountId)
    notice.value = result.mailed
        ? t('passkeys.onboardAgain.mailed')
        : t('passkeys.onboardAgain.unreachable')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}

async function issueCode() {
  failure.value = null
  try {
    passkeyCode.value = await members.issuePasskeyCode(props.member.accountId)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

/**
 * Takes the code back once it has been read out, and says nothing when that fails: the code dies with
 * its five minutes either way, so there is nothing for the reader to do about it.
 */
async function revokeCode() {
  if (!passkeyCode.value) return
  passkeyCode.value = null
  await members.revokePasskeyCode(props.member.accountId).catch(() => {})
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('passkeys.onboardAgain.title') }}</SectionHeader>
    <FailureAlert :failure="failure"/>
    <Alert v-if="notice" variant="info">{{ notice }}</Alert>

    <div class="space-y-2">
      <MutedText tag="p" size="sm">{{ t('passkeys.onboardAgain.hint') }}</MutedText>
      <SecondaryButton type="button" :disabled="busy" :icon="['fas', 'rotate-left']" @click="onboardAgain">
        {{ t('passkeys.onboardAgain.button') }}
      </SecondaryButton>
    </div>

    <div v-if="addressless && passkeyMode !== 'OFF'" class="space-y-2 border-t border-(--border) pt-4">
      <MutedText tag="p" size="sm">{{ t('passkeys.code.managerHint') }}</MutedText>
      <SecondaryButton v-if="!passkeyCode" type="button" :icon="['fas', 'fingerprint']" @click="issueCode">
        {{ t('passkeys.code.issue') }}
      </SecondaryButton>
      <PasskeyCodeDisplay v-else :code="passkeyCode.code" :qr-png="passkeyCode.qrPng" @gone="revokeCode"/>
    </div>
  </NeutralContainer>
</template>
