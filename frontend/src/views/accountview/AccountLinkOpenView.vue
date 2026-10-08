/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import LinkRequestCard from '@/components/accountlink/LinkRequestCard.vue'
import {accountLinks} from '@/api'
import type {LinkPrompt} from '@/api/generated/schema'
import {useLinkRequests} from '@/composables/useLinkRequests'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'

/**
 * Where the link in a request mail lands. The reader is signed in by the time it opens, and only the
 * account the request names finds it here; following the link answers nothing on its own, the reader
 * accepts or declines below.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const links = useLinkRequests()

const prompt = ref<LinkPrompt | null>(null)
const busy = ref(false)
const answerFailure = ref<Failure | null>(null)

const {loading, failure} = useAsyncLoader(async () => {
  prompt.value = await accountLinks.openedRequest(String(route.params.token))
})

/** The station that asks, once the request has arrived; the plain wording stands until then. */
const pageTitle = computed(() => prompt.value?.stationName ?? t('pages.account-link-open.title'))

async function answer(accepting: boolean) {
  const opened = prompt.value
  if (!opened) return
  busy.value = true
  answerFailure.value = null
  try {
    if (accepting) {
      await links.accept(opened.uid)
      showToast(t('accountLinks.accepted'), 'success')
    } else {
      await links.decline(opened.uid)
      showToast(t('accountLinks.declined'), 'info')
    }
    await router.push({name: 'account-links'})
  } catch (e) {
    answerFailure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.account-link-open.subtitle')">
    <div class="space-y-4">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure ?? answerFailure"/>
      <MutedText v-if="failure" tag="p" size="sm">{{ t('accountLinks.openFailedHint') }}</MutedText>
      <LinkRequestCard
          v-if="prompt"
          :prompt="prompt"
          :busy="busy"
          @accept="answer(true)"
          @decline="answer(false)"
      />
    </div>
  </ViewContent>
</template>
