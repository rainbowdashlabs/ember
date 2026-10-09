/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AppLink from '@/components/navigation/AppLink.vue'

/**
 * The end of the flow: everything chosen is signed, and every signer gets a copy by mail. Leads to the
 * documents and to what is still open.
 *
 * <p>The heading takes the focus as the result replaces the steps, so the reader hears that it worked.
 */
defineProps<{signed: number}>()

const {t} = useI18n()
const headingId = useId()

onMounted(() => document.getElementById(headingId)?.focus())
</script>

<template>
  <SuccessContainer class="space-y-3 max-w-3xl" data-testid="signing-done">
    <SectionHeader :id="headingId" tabindex="-1" class="focus:outline-none">{{ t('signing.done.heading') }}</SectionHeader>
    <p>{{ t('signing.done.copy') }}</p>
    <p data-testid="signing-done-count">
      {{ signed === 1 ? t('signing.done.oneSigned') : t('signing.done.signed', {count: signed}) }}
    </p>
    <div class="flex flex-wrap gap-x-6 gap-y-2">
      <AppLink href="/station/documents" :icon="['fas', 'file-lines']">{{ t('signing.done.toDocuments') }}</AppLink>
      <AppLink href="/station/requirements" :icon="['fas', 'clipboard-check']">{{ t('signing.done.toRequirements') }}</AppLink>
    </div>
  </SuccessContainer>
</template>
