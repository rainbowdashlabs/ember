/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {watch} from 'vue'
import {useRouter} from 'vue-router'
import type {PublicStationInfo} from '@/api/discovery'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const router = useRouter()
const {station, basePath} = usePublicStationAddress()

function redirect(info: PublicStationInfo | null) {
  if (!info) return
  const base = basePath.value

  if (info.landingPageSlug) {
    router.replace(`${base}/page/${info.landingPageSlug}`)
  } else if (info.hasPublicBlog) {
    router.replace(`${base}/blog`)
  } else if (info.hasPublicCalendar) {
    router.replace(`${base}/calendar`)
  } else if (info.hasPublicKb) {
    router.replace(`${base}/knowledge`)
  }
}

// Station info is provided by PublicStationShell - redirect once available
if (station.value) {
  redirect(station.value)
}
watch(station, (v) => {
  if (v) redirect(v)
})
</script>

<template>
  <div/>
</template>
