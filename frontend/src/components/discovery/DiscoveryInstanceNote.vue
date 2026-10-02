/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Which instance a station of another instance lives on, written out so that sighted readers and
 * screen readers learn it alike; the icon beside it is decoration only.
 */
defineProps<{
  host: string
}>()

const {t} = useI18n()
</script>

<template>
  <MutedText tag="p" size="sm" class="flex items-center gap-1.5 min-w-0">
    <AppIcon :icon="['fas', 'server']" aria-hidden="true" class="shrink-0"/>
    <span class="truncate">{{ t('discovery.onInstance', {host}) }}</span>
  </MutedText>
</template>
