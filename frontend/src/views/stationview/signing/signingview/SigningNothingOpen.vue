/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import AppLink from '@/components/navigation/AppLink.vue'

/** Nothing waits for the reader's signature: said plainly, with the way to what else is open. */
const {t} = useI18n()
</script>

<template>
  <InfoContainer class="space-y-3 max-w-3xl" data-testid="signing-nothing-open">
    <p>{{ t('signing.flow.nothingOpen') }}</p>
    <AppLink href="/station/requirements" :icon="['fas', 'clipboard-check']">{{ t('signing.done.toRequirements') }}</AppLink>
  </InfoContainer>
</template>
