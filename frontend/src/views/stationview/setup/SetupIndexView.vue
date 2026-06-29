/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted} from 'vue'
import {useRouter} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {stepRouteName} from '@/views/stationview/setup/steps'

const router = useRouter()
const {load, completedAt} = useSetupStatus()

onMounted(async () => {
    await load()
    if (completedAt.value) {
        router.replace('/station/dashboard/overview')
        return
    }
    router.replace({name: stepRouteName('welcome')})
})
</script>

<template>
  <div class="flex items-center justify-center py-16">
    <Spinner size="lg"/>
  </div>
</template>
