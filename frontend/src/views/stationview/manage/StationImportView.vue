/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import StationImportSection from './stationview/StationImportSection.vue'

const error = ref('')
const success = ref('')

function handleError(msg: string) { error.value = msg; success.value = '' }
function handleSuccess(msg: string) { success.value = msg; error.value = '' }
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>
      <StationImportSection @error="handleError" @success="handleSuccess"/>
    </div>
  </ViewContent>
</template>
