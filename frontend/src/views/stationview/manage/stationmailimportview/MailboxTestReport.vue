/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import type {MailboxTestResult} from '@/api/mailImport'

/**
 * What a connection test came to.
 *
 * <p>The folders are the point rather than a nicety: the commonest mistake in setting one of these up is
 * a folder spelled the way the person says it rather than the way the provider does.
 */
defineProps<{
  result: MailboxTestResult
  folder: string
}>()

const {t} = useI18n()
</script>

<template>
  <Alert :variant="result.connected ? 'success' : 'error'">
    <template v-if="result.connected">
      <p>{{ t('mailImport.testConnected') }}</p>
      <p v-if="!result.folderExists" class="mt-1">{{ t('mailImport.testFolderMissing', {folder}) }}</p>
      <p class="mt-1 break-words">{{ t('mailImport.testFolders', {folders: result.folders.join(', ')}) }}</p>
    </template>
    <p v-else class="break-words whitespace-pre-wrap">{{ result.error }}</p>
  </Alert>
</template>
