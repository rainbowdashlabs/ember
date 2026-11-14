/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import AppIcon from '@/components/display/AppIcon.vue'
import {stationLogoUrl} from '@/api/media'

/**
 * The picture of a station chat: its station's logo, and a station mark until it has loaded or where
 * the station has none.
 */
defineProps<{
  stationUid: string
}>()

const loaded = ref(false)
const missing = ref(false)
</script>

<template>
  <div class="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary/10">
    <img v-if="!missing" v-show="loaded" :src="stationLogoUrl(stationUid, 72)" alt="" class="h-full w-full object-contain"
         @load="loaded = true" @error="missing = true"/>
    <AppIcon v-if="!loaded" :icon="['fas', 'house-flag']" class="h-4 w-4 text-primary"/>
  </div>
</template>
