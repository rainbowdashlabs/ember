/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Alert from '@/components/feedback/Alert.vue'
import {adminSettings} from '@/api'
import type {BulkRetireResult, ResidueEntry} from '@/api/adminSettings'
import {formatDate} from '@/util/format'
import {describeFailure} from '@/util/failure'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * The residue and the retiring. The list holds password owners with no exercised passkey; all
 * three answers to it are a person's decisions about somebody they know. The bulk action acts
 * only on the accounts a passkey already proved, ends no sessions, and says how many it passed
 * over rather than pretending it covered everything.
 */
const {t} = useI18n()

const residue = ref<ResidueEntry[] | null>(null)
const bulkResult = ref<BulkRetireResult | null>(null)

const {loading, failure, reload: load} = useAsyncLoader(async () => {
  residue.value = await adminSettings.getPasskeyResidue()
}, {autoLoad: false})

async function retireAll() {
  failure.value = null
  bulkResult.value = null
  try {
    bulkResult.value = await adminSettings.retireAllPasswords()
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await load()
}

function reachability(entry: ResidueEntry): string {
  if (entry.reachable) return t('adminSecurity.passkeys.residueReachable')
  if (entry.hasGuardian) return t('adminSecurity.passkeys.residueGuardian')
  return t('adminSecurity.passkeys.residueQrOnly')
}
</script>

<template>
  <div class="space-y-2">
    <ButtonRow>
      <SecondaryButton type="button" :disabled="loading" @click="load">
        {{ t('adminSecurity.passkeys.loadResidue') }}
      </SecondaryButton>
      <SecondaryButton type="button" @click="retireAll">
        {{ t('adminSecurity.passkeys.retireAll') }}
      </SecondaryButton>
    </ButtonRow>
    <FailureAlert :failure="failure"/>
    <Alert v-if="bulkResult" variant="info">
      {{ t('adminSecurity.passkeys.retireAllResult', {retired: bulkResult.retired, passedOver: bulkResult.passedOver}) }}
    </Alert>
    <InfoContainer v-if="residue" class="space-y-1 text-sm">
      <div class="font-medium">{{ t('adminSecurity.passkeys.residueTitle', {count: residue.length}) }}</div>
      <MutedText tag="p" size="sm">{{ t('adminSecurity.passkeys.residueHint') }}</MutedText>
      <ul class="divide-y divide-(--border)">
        <li v-for="entry in residue" :key="entry.accountId" class="flex flex-wrap justify-between gap-2 py-1">
          <span>{{ entry.firstName }} {{ entry.lastName }}</span>
          <MutedText tag="span" size="sm">
            {{ entry.lastSignInAt
              ? t('adminSecurity.passkeys.residueLastSeen', {date: formatDate(entry.lastSignInAt)})
              : t('adminSecurity.passkeys.residueNeverSeen') }}
            · {{ reachability(entry) }}
          </MutedText>
        </li>
      </ul>
    </InfoContainer>
  </div>
</template>
