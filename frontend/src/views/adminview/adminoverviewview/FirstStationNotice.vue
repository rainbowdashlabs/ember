/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import {isFirstStationNeeded} from '@/api/stations'
import {FIRST_STATION_PATH} from '@/util/signInLanding'

/**
 * The way forward for an administrator who reached the dashboard of an instance without any station:
 * the first one is founded before anything else can happen here.
 */
const {t} = useI18n()
const needed = ref(false)

onMounted(async () => {
  needed.value = await isFirstStationNeeded().catch(() => false)
})
</script>

<template>
  <Alert v-if="needed" variant="info" data-testid="first-station-notice">
    <span class="mr-2">{{ t('firstStation.adminNotice') }}</span>
    <AppLink :href="FIRST_STATION_PATH" :icon="['fas', 'building']">{{ t('firstStation.adminNoticeAction') }}</AppLink>
  </Alert>
</template>
