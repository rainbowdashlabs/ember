/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import type {ProbeResult} from '@/api/generated/schema'

/** Whether a storage answered its connection test; nothing at all before one was asked. */
defineProps<{
    outcome: ProbeResult | null
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="outcome" class="text-sm">
        <Alert v-if="outcome.healthy" variant="success">{{ t('storageBackend.probe.ok') }}</Alert>
        <Alert v-else variant="error">{{ t('storageBackend.probe.failed', {reason: outcome.error ?? ''}) }}</Alert>
    </div>
</template>
