/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberLogoFaq} from '@/composables/useEmberLogo'
import {useSignedIn} from '@/composables/useSignedIn'
import PageHeader from '@/components/typography/PageHeader.vue'
import {openProblemReport} from '@/util/problemReportState'

const props = defineProps<{
  status: number
  title: string
  text: string
}>()

const {t} = useI18n()

/**
 * A page that is simply not there is nobody's fault and needs no report. A page that broke is ours, and
 * this is the one screen where a reader has nothing else left to try.
 */
const broke = computed(() => props.status >= 500 || props.status === 0)
const {anonymous} = useSignedIn()
const faqLogo = emberLogoFaq()

/**
 * Leaves the error state behind on the way out.
 *
 * An error page keeps rendering until the error is cleared, so a plain link to another route
 * would change the address and leave the reader looking at the same panel.
 */
function leaveTo(path: string) {
  clearError({redirect: path})
}
</script>

<template>
  <div data-testid="error-page" class="flex flex-col items-center justify-center py-20 px-4 text-center">
    <LayeredEmberLogo :layers="faqLogo.layers" :active-layers="faqLogo.activeLayers" :auto-blink="true" :bounce="true" :display-shake="true" size="w-32 h-32 mb-6" :pixel-size="512" />
    <PageHeader class="text-6xl font-extrabold text-primary mb-2">{{ status }}</PageHeader>
    <p class="text-xl font-medium mb-2">{{ title }}</p>
    <p class="text-(--text-muted) mb-8 max-w-md">{{ text }}</p>
    <div class="flex gap-3">
      <SecondaryButton data-testid="error-home" :icon="['fas', 'house']" @click="leaveTo('/?home')">
        {{ t('notFound.home') }}
      </SecondaryButton>
      <SecondaryButton v-if="anonymous" data-testid="error-login" :icon="['fas', 'right-from-bracket']" @click="leaveTo('/login')">
        {{ t('notFound.login') }}
      </SecondaryButton>
      <SecondaryButton
          v-if="broke"
          data-testid="error-report"
          :icon="['fas', 'bug']"
          @click="openProblemReport({summary: t('errorPage.reportSummary', {status})})"
      >
        {{ t('failure.report') }}
      </SecondaryButton>
    </div>
  </div>
</template>
