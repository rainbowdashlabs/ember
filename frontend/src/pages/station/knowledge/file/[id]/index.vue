/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import KbFileView from '~/views/stationview/knowledge/KbFileView.vue'

definePageMeta({
  layout: 'station',
  name: 'kb-file',
})

const route = useRoute()

const fileId = computed(() => Number(route.params.id))
</script>

<template>
  <KbFileView :file-id="fileId" />
</template>
