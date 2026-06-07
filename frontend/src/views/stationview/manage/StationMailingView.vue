/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import MailConfigSection from './stationview/MailConfigSection.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {hasPermission} = useSession()
const router = useRouter()
if (!hasPermission(StationPermission.STATION_MAIL)) {
  router.replace({name: 'dashboard-overview'})
}

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
      <MailConfigSection @error="handleError" @success="handleSuccess"/>
    </div>
  </ViewContent>
</template>
