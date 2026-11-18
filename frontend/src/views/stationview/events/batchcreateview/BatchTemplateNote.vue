/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'

/**
 * What an applied appointment template hands to every appointment of a batch beyond what the form
 * shows, and what of it the batch leaves out. Shows nothing where there is neither.
 */
defineProps<{
  carried: string[]
  leftOut: string[]
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="carried.length > 0 || leftOut.length > 0" class="space-y-2">
    <Alert v-if="carried.length > 0" variant="info" data-testid="batch-template-carried">
      <p>{{ t('batchCreate.carried') }}</p>
      <ul class="list-disc pl-5">
        <li v-for="entry in carried" :key="entry">{{ entry }}</li>
      </ul>
    </Alert>
    <Alert v-if="leftOut.length > 0" variant="info" data-testid="batch-template-left-out">
      <p>{{ t('batchCreate.leftOut') }}</p>
      <ul class="list-disc pl-5">
        <li v-for="entry in leftOut" :key="entry">{{ entry }}</li>
      </ul>
    </Alert>
  </div>
</template>
