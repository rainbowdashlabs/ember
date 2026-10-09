/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import LinkRequestCard from './LinkRequestCard.vue'
import {answeredKey} from './linkPromptText'
import type {LinkPrompt} from '@/api/generated/schema'
import {useLinkRequests} from '@/composables/useLinkRequests'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'

/**
 * Every request of a station or an association waiting for the reader's account, each answered on its
 * own. Used by the prompt after sign-in and by the account page, so both read and answer alike.
 */
const {t} = useI18n()
const links = useLinkRequests()
const busy = ref<string | null>(null)
const failure = ref<Failure | null>(null)

async function answer(prompt: LinkPrompt, accepting: boolean) {
  busy.value = prompt.uid
  failure.value = null
  try {
    if (accepting) {
      await links.accept(prompt.uid)
      showToast(t(answeredKey(prompt, true)), 'success')
    } else {
      await links.decline(prompt.uid)
      showToast(t(answeredKey(prompt, false)), 'info')
    }
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div class="space-y-3">
    <FailureAlert :failure="failure"/>
    <EmptyHint v-if="links.prompts.value.length === 0">{{ t('accountLinks.none') }}</EmptyHint>
    <LinkRequestCard
        v-for="prompt in links.prompts.value"
        :key="prompt.uid"
        :prompt="prompt"
        :busy="busy === prompt.uid"
        @accept="answer(prompt, true)"
        @decline="answer(prompt, false)"
    />
  </div>
</template>
