/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {transfer} from '@/api'

const emit = defineEmits<{
  error: [msg: string]
  success: [msg: string]
}>()

const {t} = useI18n()

const creatingToken = ref(false)
const transferToken = ref('')

async function createToken() {
  creatingToken.value = true
  transferToken.value = ''
  try {
    const result = await transfer.createTransferToken()
    transferToken.value = result.token
  } catch {
    emit('error', t('common.error'))
  } finally {
    creatingToken.value = false
  }
}

async function copyToken() {
  await navigator.clipboard.writeText(transferToken.value)
  emit('success', t('stationManage.transferTokenCopied'))
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.transferTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.transferHint') }}</p>
    <PrimaryButton :disabled="creatingToken" @click="createToken">
      {{ creatingToken ? t('stationManage.transferCreating') : t('stationManage.transferCreate') }}
    </PrimaryButton>
    <div v-if="transferToken" class="space-y-2">
      <FieldLabel>{{ t('stationManage.transferTokenLabel') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">{{ transferToken }}</code>
        <SecondaryButton @click="copyToken">
          <font-awesome-icon :icon="['fas', 'copy']" />
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
