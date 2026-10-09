/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkRequestList from './LinkRequestList.vue'
import {useLinkRequests} from '@/composables/useLinkRequests'
import {hasSessionCookie} from '@/api/sessionCookie'
import {isPublicRoute} from '@/util/publicRoute'

/**
 * The question put after sign-in when a station asked to link the reader's account to one of its
 * members. It can be put off: the requests stay under Account until they are answered or run out.
 *
 * <p>The sign-in screens ask right after a session starts, the same moment they check the consent. A
 * sign-in that ends in a full page load, such as the one after a second factor, and a session that
 * was already there are asked when the page loads.
 */
const {t} = useI18n()
const route = useRoute()
const links = useLinkRequests()

const open = computed({
  get: () => links.promptOpen.value,
  set: (value: boolean) => links.setPromptOpen(value),
})

onMounted(() => {
  if (hasSessionCookie() && !isPublicRoute(route.path, route.meta)) void links.checkAfterSignIn()
})
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-4 p-4">
      <SectionHeader>{{ t('accountLinks.promptTitle') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('accountLinks.promptIntro') }}</MutedText>
      <LinkRequestList/>
      <ButtonRow align="end">
        <SecondaryButton data-cancel @click="open = false">{{ t('accountLinks.later') }}</SecondaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
