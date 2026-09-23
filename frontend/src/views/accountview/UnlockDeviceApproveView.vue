/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Alert from '@/components/feedback/Alert.vue'
import NumberMatchPicker from './unlockdeviceapproveview/NumberMatchPicker.vue'
import {passkeys} from '@/api'
import type {DeviceLookup} from '@/api/passkeys'
import {apiErrorStatus} from '@/util/apiError'
import {describeFailure, FailureKind} from '@/util/failure'
import {formatDateTime} from '@/util/format'

/**
 * The approving half of the device handshake: type the code the new device shows, see what is
 * about to be approved in large plain words, and approve only when you are sitting at that
 * device yourself. The approval itself stands behind the step-up proof.
 */
const {t} = useI18n()
const route = useRoute()

const code = ref('')
const details = ref<DeviceLookup | null>(null)
const error = ref('')
const done = ref(false)
const busy = ref(false)
const forAccountId = ref('')

/**
 * A sign-in a guardian may make for somebody in their care offers the choice. The other two grants
 * are always for the reader themselves: a passkey belongs to whoever approved it, and a step-up
 * stamps the session that asked for it.
 */
const offersCandidates = computed(() => (details.value?.candidates.length ?? 0) > 1)

/** What approving this actually does, said plainly. The three differ enormously. */
const purposeSentence = computed(() => {
  const purpose = details.value?.purpose
  if (purpose === 'SIGN_IN') return t('passkeys.approve.purposeSignIn')
  if (purpose === 'STEP_UP') {
    const category = details.value?.stepUpCategory
    return category
        ? t('passkeys.approve.purposeStepUpCategory', {category: t(`twoFactor.stepUp.category.${category}`)})
        : t('passkeys.approve.purposeStepUp')
  }
  return t('passkeys.approve.purposePasskey')
})

/**
 * What a refusal means here.
 *
 * <p>Four answers, and they ask different things of the reader. An unknown code is a typo or a code
 * raised for somebody else, and the server does not distinguish those on purpose. A refusal for
 * asking too often can be waited out, and saying so is the difference between waiting twenty
 * seconds and giving up. A wrong number ends the request outright, which the reader has to be told
 * or they will sit waiting for something that is never coming.
 */
function refusalText(e: unknown): string {
  const status = apiErrorStatus(e)
  if (status === 404) return t('passkeys.approve.unknownCode')
  if (status === 409) return t('passkeys.approve.wrongNumber')
  const failure = describeFailure(e, t)
  if (failure.kind === FailureKind.TOO_OFTEN) return `${failure.message} ${failure.guidance}`
  return t('common.error')
}

async function lookup() {
  error.value = ''
  busy.value = true
  try {
    details.value = await passkeys.deviceLookup(code.value)
    forAccountId.value = String(details.value.candidates[0]?.accountId ?? '')
  } catch (e) {
    details.value = null
    error.value = refusalText(e)
  } finally {
    busy.value = false
  }
}

/**
 * Approves, with the number the reader chose.
 *
 * <p>A wrong choice is the end of the request rather than a retry, so the screen goes back to the
 * beginning and says why: there is nothing left to press here, and the device has to ask again.
 */
async function approve(pickedNumber: number) {
  error.value = ''
  busy.value = true
  try {
    await passkeys.deviceApprove(
        code.value,
        pickedNumber,
        offersCandidates.value ? Number(forAccountId.value) : undefined)
    done.value = true
  } catch (e) {
    const refused = refusalText(e)
    if (apiErrorStatus(e) === 409) {
      code.value = ''
      details.value = null
    }
    error.value = refused
  } finally {
    busy.value = false
  }
}

function reset() {
  code.value = ''
  details.value = null
  done.value = false
  error.value = ''
}

/**
 * A code that arrived in the address, which is how scanning the QR gets here.
 *
 * <p>It is read and the request looked up at once, so the reader lands on the number rather than
 * on a filled in field and a button. Nothing is approved by arriving: the number still has to be
 * matched, and that is the step a forwarded QR cannot get past.
 */
onMounted(() => {
  const scanned = route.query.code
  if (typeof scanned !== 'string' || !scanned) return
  code.value = scanned
  void lookup()
})
</script>

<template>
  <ViewContent :title="t('pages.account-unlock-device.title')" :subtitle="t('pages.account-unlock-device.subtitle')">
    <div class="max-w-xl mx-auto space-y-6 p-4">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('passkeys.approve.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('passkeys.approve.hint') }}</MutedText>
        <FailureAlert :message="error"/>

        <template v-if="done">
          <Alert variant="success">{{ t('passkeys.approve.done') }}</Alert>
          <SecondaryButton type="button" @click="reset">{{ t('passkeys.approve.another') }}</SecondaryButton>
        </template>

        <template v-else-if="!details">
          <TextInput v-model="code" placeholder="K7RM-2WQD" class="font-mono tracking-widest"/>
          <PrimaryButton type="button" :disabled="busy || code.replace(/[\s-]/g, '').length < 8" @click="lookup">
            {{ t('passkeys.approve.lookup') }}
          </PrimaryButton>
        </template>

        <template v-else>
          <div class="space-y-1 text-sm">
            <div class="font-medium text-base">{{ details.userAgent || t('passkeys.approve.unknownDevice') }}</div>
            <div>{{ t('passkeys.approve.place', {place: details.country || '?'}) }}</div>
            <div>{{ t('passkeys.approve.when', {when: formatDateTime(details.createdAt)}) }}</div>
          </div>
          <Alert variant="info">{{ purposeSentence }}</Alert>
          <Alert v-if="details.stepUpSubject" variant="info">
            {{ t('passkeys.approve.purposeStepUpFor', {name: details.stepUpSubject}) }}
          </Alert>

          <div v-if="offersCandidates" class="space-y-1">
            <FieldLabel>{{ t('passkeys.approve.signInFor') }}</FieldLabel>
            <SelectInput v-model="forAccountId" class="w-full" data-testid="approve-for">
              <option v-for="candidate in details.candidates" :key="candidate.accountId" :value="String(candidate.accountId)">
                {{ candidate.name }}
              </option>
            </SelectInput>
          </div>

          <Alert variant="error">{{ t('passkeys.approve.warning') }}</Alert>

          <NumberMatchPicker :busy="busy" :choices="details.numberChoices" @pick="approve"/>

          <SecondaryButton class="w-full" type="button" :disabled="busy" @click="reset">
            {{ t('common.cancel') }}
          </SecondaryButton>
        </template>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
