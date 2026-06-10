/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {inject, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {Ref} from 'vue'
import type {PublicStationInfo} from '@/api/discovery'

const route = useRoute()
const router = useRouter()
const station = inject<Ref<PublicStationInfo | null>>('publicStation')

function redirect(info: PublicStationInfo | null) {
  if (!info) return
  const uid = route.params.stationUid as string
  const base = `/public/station/${uid}`

  if (info.landingPageSlug) {
    router.replace(`${base}/page/${info.landingPageSlug}`)
  } else if (info.hasPublicCalendar) {
    router.replace(`${base}/calendar`)
  } else if (info.hasPublicKb) {
    router.replace(`${base}/knowledge`)
  }
}

// Station info is provided by PublicStationShell — redirect once available
if (station?.value) {
  redirect(station.value)
}
watch(() => station?.value, (v) => {
  if (v) redirect(v)
})
</script>

<template>
  <div/>
</template>
