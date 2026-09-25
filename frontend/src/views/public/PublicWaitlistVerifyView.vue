/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {waitingList} from '@/api'
import {describeFailure, FailureKind, type Failure} from '@/util/failure'
import {socialMeta} from '@/util/socialMeta'

const {t} = useI18n()
const route = useRoute()

useHead(computed(() => {
  const title = t('waitingList.publicRegistration.verifyPageTitle')
  return {
    title,
    meta: [
      {name: 'robots', content: 'noindex, nofollow'},
      {name: 'referrer', content: 'no-referrer'},
      ...socialMeta({title, description: t('waitingList.publicRegistration.verifyPageDescription')}),
    ],
  }
}))

const loading = ref(true)
const success = ref(false)
const failure = ref<Failure | null>(null)

onMounted(async () => {
  const token = route.params.token as string
  if (!token) {
    failure.value = spentLink()
    loading.value = false
    return
  }
  try {
    await waitingList.verifyPublicRegistration(token)
    success.value = true
  } catch (e) {
    failure.value = describeVerification(e)
  } finally {
    loading.value = false
  }
})

/**
 * Why the confirmation did not go through.
 *
 * <p>A link that has already been used, or that expired, is the ordinary outcome here and not a fault:
 * the reader needs a fresh mail, not a bug report. Anything else is Ember failing to do the one thing
 * this page exists for, and somebody who came here from a mail with no account and no station to ask
 * should be told that plainly, and handed the way to say so.
 */
function describeVerification(e: unknown): Failure {
  const described = describeFailure(e, t)
  const spent = described.kind === FailureKind.GONE
      || described.kind === FailureKind.CONFLICT
      || described.kind === FailureKind.REJECTED
  return spent ? spentLink() : described
}

function spentLink(): Failure {
  return {
    kind: FailureKind.GONE,
    message: t('waitingList.publicRegistration.verifyError'),
    guidance: t('waitingList.publicRegistration.verifyErrorGuidance'),
    reportable: false,
  }
}
</script>

<template>
  <div class="max-w-lg mx-auto mt-16 px-4">
    <Spinner v-if="loading" size="lg"/>

    <SuccessContainer v-if="success" class="space-y-3 text-center">
      <font-awesome-icon :icon="['fas', 'circle-check']" class="text-4xl text-success"/>
      <SectionHeader>{{ t('waitingList.publicRegistration.verifyTitle') }}</SectionHeader>
      <p>{{ t('waitingList.publicRegistration.verifyText') }}</p>
    </SuccessContainer>

    <FailureAlert :failure="failure"/>
  </div>
</template>
