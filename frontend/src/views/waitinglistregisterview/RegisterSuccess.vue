/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const props = defineProps<{ statusLink: string }>()

const { t } = useI18n()

async function copyStatusLink() {
  await navigator.clipboard.writeText(props.statusLink)
}
</script>

<template>
  <Alert variant="success">{{ t('waitingList.register.success') }}</Alert>

  <NeutralContainer class="space-y-3">
    <p class="text-sm font-medium">{{ t('waitingList.register.saveLink') }}</p>
    <p class="text-xs text-(--text-muted)">{{ t('waitingList.register.saveLinkHint') }}</p>
    <div class="flex items-center gap-2">
      <code class="flex-1 text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 rounded break-all select-all">{{ props.statusLink }}</code>
      <PrimaryButton @click="copyStatusLink">
        <font-awesome-icon :icon="['fas', 'copy']" />
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
