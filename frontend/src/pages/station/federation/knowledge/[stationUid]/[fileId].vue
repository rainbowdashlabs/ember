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
  name: 'federated-kb-file',
})

const route = useRoute()

const stationUid = computed(() => String(route.params.stationUid))
const fileId = computed(() => Number(route.params.fileId))
</script>

<template>
  <KbFileView :station-uid="stationUid" :file-id="fileId" />
</template>
