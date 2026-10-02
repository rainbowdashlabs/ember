/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import type {IssuedOneTimePassword} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'
import {printSheet, type Sheet} from '@/util/printSheet'

/**
 * The one moment a one-time password is ever shown: the name to sign in with, the password and the
 * address of the instance, ready to be copied or printed for the person who receives them. Nothing
 * here can be fetched again once the dialog is closed.
 */
const props = defineProps<{issued: IssuedOneTimePassword}>()

const open = defineModel<boolean>({required: true})

const {t} = useI18n()
const copied = ref(false)

const instanceAddress = computed(() => (import.meta.client ? window.location.origin : ''))

const sheet = computed<Sheet>(() => ({
  title: t('oneTimePassword.dialog.title', {name: props.issued.name}),
  lines: [
    {label: t('oneTimePassword.dialog.loginName'), value: props.issued.loginName},
    {label: t('oneTimePassword.dialog.password'), value: props.issued.password},
    {label: t('oneTimePassword.dialog.instance'), value: instanceAddress.value},
    {label: t('oneTimePassword.dialog.expires'), value: formatDateTime(props.issued.expiresAt)},
  ],
  note: t('oneTimePassword.dialog.note'),
}))

async function copy() {
  const text = [sheet.value.title, ...sheet.value.lines.map(line => `${line.label}: ${line.value}`)].join('\n')
  await navigator.clipboard.writeText(text)
  copied.value = true
}
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4" data-testid="one-time-password-dialog">
      <SubHeader>{{ sheet.title }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('oneTimePassword.dialog.hint') }}</MutedText>
      <dl class="space-y-3">
        <div v-for="line in sheet.lines" :key="line.label">
          <dt><DetailLabel>{{ line.label }}</DetailLabel></dt>
          <dd class="font-mono text-lg break-all">{{ line.value }}</dd>
        </div>
      </dl>
      <MutedText tag="p" size="sm">{{ sheet.note }}</MutedText>
      <ButtonRow align="end">
        <SecondaryButton :icon="['fas', 'copy']" @click="copy">
          {{ copied ? t('oneTimePassword.dialog.copied') : t('oneTimePassword.dialog.copy') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'print']" @click="printSheet(sheet)">
          {{ t('oneTimePassword.dialog.print') }}
        </SecondaryButton>
        <PrimaryButton data-confirm @click="open = false">{{ t('common.close') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
