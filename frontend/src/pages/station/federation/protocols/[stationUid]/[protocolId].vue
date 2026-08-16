/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import FederatedProtocolView from '~/views/stationview/protocol/FederatedProtocolView.vue'

definePageMeta({
  layout: 'station',
  name: 'federated-protocol',
})

const route = useRoute()

const stationUid = computed(() => String(route.params.stationUid))
const protocolId = computed(() => Number(route.params.protocolId))
</script>

<template>
  <FederatedProtocolView :station-uid="stationUid" :protocol-id="protocolId" />
</template>
