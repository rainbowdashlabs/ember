/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useMapsConfig} from '@/composables/useMapsConfig'

const {load} = useMapsConfig()
const attribution = ref('')

onMounted(async () => {
  try {
    const config = await load()
    attribution.value = config.attribution || ''
  } catch {
    attribution.value = ''
  }
})
</script>

<template>
  <p v-if="attribution" class="text-xs text-(--text-muted)" v-html="attribution"/>
</template>
