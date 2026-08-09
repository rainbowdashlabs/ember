/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useAuthImage} from '@/composables/useAuthImage'

const props = defineProps<{
  src: string
  alt?: string
}>()

const {src: blobUrl, loading, failed} = useAuthImage(() => props.src)
</script>

<template>
  <img v-if="blobUrl" :src="blobUrl" :alt="alt ?? ''" v-bind="$attrs"/>
  <slot v-else-if="failed" name="error"/>
  <slot v-else-if="loading" name="loading"/>
</template>
