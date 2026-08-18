/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import StorageScopeChoice from '@/components/consent/StorageScopeChoice.vue'
import type {StorageNecessityName} from '@/api/storage'

const props = defineProps<{
  consentLoading: boolean
  consentHtml: string
}>()

/** The optional groups the visitor allows. Required storage is not part of the choice. */
const scopes = defineModel<StorageNecessityName[]>('scopes', {required: true})

const emit = defineEmits<{
  (e: 'accept'): void
  (e: 'deny'): void
  (e: 'showPrivacy'): void
  (e: 'showTos'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader class="font-semibold text-lg">{{ t('storageConsent.title') }}</SectionHeader>

    <Spinner v-if="props.consentLoading" size="sm"/>
    <div v-else-if="props.consentHtml" class="legal-content max-h-64 overflow-y-auto text-sm border border-(--border) rounded-lg p-3" v-html="props.consentHtml"/>
    <p v-else class="text-sm text-(--text-muted)">{{ t('storageConsent.description') }}</p>

    <StorageScopeChoice v-model="scopes"/>

    <div class="flex gap-4">
      <LinkButton @click="emit('showPrivacy')">{{ t('storageConsent.privacyPolicy') }}</LinkButton>
      <LinkButton @click="emit('showTos')">{{ t('storageConsent.tos') }}</LinkButton>
    </div>

    <div class="flex gap-3">
      <SuccessButton class="flex-1" @click="emit('accept')">
        {{ t('storageConsent.accept') }}
      </SuccessButton>
      <ErrorButton class="flex-1" @click="emit('deny')">
        {{ t('storageConsent.deny') }}
      </ErrorButton>
    </div>
  </NeutralContainer>
</template>
