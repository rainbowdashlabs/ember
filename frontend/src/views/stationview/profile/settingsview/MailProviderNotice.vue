/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import {mailProviderLabel} from '@/util/mailProviders'
import type {MailProvider} from '@/api/generated/schema'

/**
 * Which providers a member's mail goes out through, each with its privacy notice. All of them are
 * named, in the order they are tried, because one only reached when the ones before it fail still
 * receives the member's address.
 */
const props = defineProps<{
  providers: MailProvider[]
}>()

const {t} = useI18n()

const labelOf = (provider: MailProvider) => mailProviderLabel(provider, t('userSettings.mailProviderOwnServer'))

const only = computed(() => props.providers.length === 1 ? props.providers[0] : undefined)
</script>

<template>
  <InfoContainer class="space-y-2">
    <template v-if="only">
      <p class="text-sm">{{ t('userSettings.mailProviderInfo', {provider: labelOf(only)}) }}</p>
      <p v-if="only.url" class="text-xs">
        <a :href="only.url" target="_blank" rel="noopener noreferrer" class="text-primary hover:underline">
          {{ t('userSettings.mailProviderPrivacy') }}
        </a>
      </p>
    </template>
    <template v-else-if="props.providers.length > 1">
      <p class="text-sm">{{ t('userSettings.mailProvidersInfo') }}</p>
      <ol class="text-sm list-decimal pl-5 space-y-1">
        <li v-for="(provider, index) in props.providers" :key="index">
          {{ labelOf(provider) }}
          <a v-if="provider.url" :href="provider.url" target="_blank" rel="noopener noreferrer"
             class="text-xs text-primary hover:underline ml-2">
            {{ t('userSettings.mailProviderPrivacy') }}
          </a>
        </li>
      </ol>
    </template>
    <p v-else class="text-sm">{{ t('userSettings.mailProviderInfo', {provider: t('userSettings.mailProviderUnknown')}) }}</p>
  </InfoContainer>
</template>
