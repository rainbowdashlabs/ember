/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {stationLogoUrl} from '@/api/media'
import {initialsOf} from './stationList'

/**
 * A station's logo at 64 pixels, or its initials where it has none. Whether it has one is learned by
 * asking for it: the logo lives in the station's own storage, which the list does not reach into for
 * every station it shows. The initials stand until the picture has arrived, so a station without a
 * logo never flashes a broken picture; the answer that there is none is cached like the logo itself.
 * The picture is decorative, the station's name stands beside it.
 */
const props = defineProps<{
  uid: string
  name: string
}>()

const loaded = ref(false)
const missing = ref(false)
watch(() => props.uid, () => {
  loaded.value = false
  missing.value = false
})

const initials = computed(() => initialsOf(props.name))
</script>

<template>
  <div class="w-16 h-16 shrink-0">
    <img
        v-if="!missing"
        v-show="loaded"
        :src="stationLogoUrl(uid, 128)"
        alt=""
        class="w-16 h-16 rounded-full object-cover"
        data-testid="station-logo"
        @load="loaded = true"
        @error="missing = true"
    />
    <div
        v-if="!loaded"
        class="w-16 h-16 rounded-full bg-[var(--bg-accent)] flex items-center justify-center text-lg font-semibold text-[var(--text-muted)]"
        aria-hidden="true"
        data-testid="station-initials"
    >
      {{ initials }}
    </div>
  </div>
</template>
