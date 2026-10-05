/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import StorageScopeChoice from '@/components/consent/StorageScopeChoice.vue'
import {OPTIONAL_NECESSITIES, type StorageNecessityName} from '@/api/storage'

/**
 * The consent asked before anybody signs in or sets up their account: the terms, the privacy policy
 * and what may be kept in the browser.
 *
 * <p>Every answer is one button of the same kind, side by side. Allowing everything is not dressed
 * up as the obvious choice and keeping only the necessary is not hidden behind a settings page or
 * painted as a warning; no optional group starts switched on. Declining is offered as plainly, with
 * what it means said next to it.
 */
const props = defineProps<{
  consentLoading: boolean
  consentHtml: string
}>()

/** The optional groups the visitor allows. Required storage is not part of the choice. */
const scopes = defineModel<StorageNecessityName[]>('scopes', {required: true})

const emit = defineEmits<{
  accept: []
  deny: []
  showPrivacy: []
  showTos: []
}>()

const {t} = useI18n()

function acceptWith(chosen: StorageNecessityName[]) {
  scopes.value = chosen
  emit('accept')
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader class="font-semibold text-lg">{{ t('storageConsent.title') }}</SectionHeader>

    <Spinner v-if="props.consentLoading" size="sm"/>
    <div v-else-if="props.consentHtml" class="legal-content max-h-[60vh] overflow-y-auto text-sm border border-(--border) rounded-lg p-4" v-html="props.consentHtml"/>
    <p v-else class="text-sm text-(--text-muted)">{{ t('storageConsent.description') }}</p>

    <StorageScopeChoice v-model="scopes"/>

    <ButtonRow pair>
      <LinkButton @click="emit('showPrivacy')">{{ t('storageConsent.privacyPolicy') }}</LinkButton>
      <LinkButton @click="emit('showTos')">{{ t('storageConsent.tos') }}</LinkButton>
    </ButtonRow>

    <MutedText tag="p" size="sm">{{ t('storageConsent.answerHint') }}</MutedText>

    <ButtonRow>
      <SecondaryButton class="sm:flex-1" @click="acceptWith([])">{{ t('storageConsent.necessaryOnly') }}</SecondaryButton>
      <SecondaryButton class="sm:flex-1" @click="acceptWith(scopes)">{{ t('storageConsent.acceptChoice') }}</SecondaryButton>
      <SecondaryButton class="sm:flex-1" @click="acceptWith([...OPTIONAL_NECESSITIES])">{{ t('storageConsent.acceptAll') }}</SecondaryButton>
      <SecondaryButton class="sm:flex-1" @click="emit('deny')">{{ t('storageConsent.deny') }}</SecondaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
