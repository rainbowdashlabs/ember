/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PasskeysResidue from '@/views/adminview/adminsecuritytwofactorview/PasskeysResidue.vue'
import {adminSettings} from '@/api'
import type {PasskeysConfig, PasswordlessReport} from '@/api/adminSettings'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()

const props = defineProps<{config: PasskeysConfig}>()

const report = ref<PasswordlessReport | null>(null)
const reportLoading = ref(false)

/** Mail that worked in March is not mail that works in September. */
const mailStale = computed(() => {
  if (!props.config.lastMailSentAt) return false
  return Date.now() - new Date(props.config.lastMailSentAt).getTime() > 1000 * 60 * 60 * 24 * 90
})

async function loadReport() {
  reportLoading.value = true
  try {
    report.value = await adminSettings.getPasswordlessReport()
  } finally {
    reportLoading.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- What the instance can check about itself; what a browser sees it cannot. -->
    <InfoContainer class="space-y-1 text-sm">
      <div class="font-medium">{{ t('adminSecurity.passkeys.readinessTitle') }}</div>
      <div>{{ t('adminSecurity.passkeys.rpIdLine', {rpId: config.rpId}) }}</div>
      <div v-if="config.lastMailSentAt">
        {{ t('adminSecurity.passkeys.lastMail', {when: formatDateTime(config.lastMailSentAt)}) }}
        <span v-if="mailStale" class="font-medium"> {{ t('adminSecurity.passkeys.mailStale') }}</span>
      </div>
      <div v-else class="font-medium">{{ t('adminSecurity.passkeys.noMailYet') }}</div>
      <div v-if="config.dependentAccounts > 0">
        {{ t('adminSecurity.passkeys.dependentAccounts', {count: config.dependentAccounts}) }}
      </div>
      <MutedText tag="div" size="sm">{{ t('adminSecurity.passkeys.readinessLimit') }}</MutedText>
    </InfoContainer>

    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-center">
      <div>
        <div class="text-2xl font-semibold">{{ config.accountsWithTriedPasskey }}</div>
        <MutedText tag="div" size="sm">{{ t('adminSecurity.passkeys.figureTried') }}</MutedText>
      </div>
      <div>
        <div class="text-2xl font-semibold">{{ config.accountsWithPassword }}</div>
        <MutedText tag="div" size="sm">{{ t('adminSecurity.passkeys.figurePassword') }}</MutedText>
      </div>
      <div>
        <div class="text-2xl font-semibold">{{ config.accountsWithPasswordAndNoPasskey }}</div>
        <MutedText tag="div" size="sm">{{ t('adminSecurity.passkeys.figureStuck') }}</MutedText>
      </div>
    </div>

    <div class="space-y-2">
      <SecondaryButton type="button" :disabled="reportLoading" @click="loadReport">
        {{ t('adminSecurity.passkeys.loadReport') }}
      </SecondaryButton>
      <InfoContainer v-if="report" class="space-y-1 text-sm">
        <div>{{ t('adminSecurity.passkeys.reportKeepPassword', {count: report.wouldKeepPassword}) }}</div>
        <div>{{ t('adminSecurity.passkeys.reportWithoutPasskey', {count: report.withoutPasskey}) }}</div>
        <div>{{ t('adminSecurity.passkeys.reportQrOnly', {count: report.reachableOnlyByQr}) }}</div>
        <div>{{ t('adminSecurity.passkeys.reportDormant', {count: report.dormantForAYear}) }}</div>
      </InfoContainer>
    </div>

    <PasskeysResidue/>
  </div>
</template>
