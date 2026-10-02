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
 * Which instance a station of another instance lives on: its address alone on screen, with the word
 * for it read out to screen readers only; the icon beside it is decoration.
 */
defineProps<{
  host: string
}>()

const {t} = useI18n()
</script>

<template>
  <MutedText tag="p" size="sm" class="flex items-center gap-1.5 min-w-0">
    <AppIcon :icon="['fas', 'server']" aria-hidden="true" class="shrink-0"/>
    <span class="truncate"><span class="sr-only">{{ t('discovery.instanceLabel') }}</span>{{ host }}</span>
  </MutedText>
</template>
