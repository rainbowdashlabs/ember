/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import LinkRequestList from '@/components/accountlink/LinkRequestList.vue'
import {useLinkRequests} from '@/composables/useLinkRequests'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * The requests stations sent to link the reader's account to one of their members, the place the
 * prompt after sign-in leads back to once it was put off.
 */
const {t} = useI18n()
const links = useLinkRequests()

const {loading, failure} = useAsyncLoader(() => links.refresh())
</script>

<template>
  <ViewContent :title="t('pages.account-links.title')" :subtitle="t('pages.account-links.subtitle')">
    <div class="space-y-4">
      <MutedText tag="p" size="sm">{{ t('accountLinks.pageIntro') }}</MutedText>
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <LinkRequestList v-if="!loading && !failure"/>
    </div>
  </ViewContent>
</template>
