/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {useStations} from '@/composables/useStations'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const props = withDefaults(defineProps<{
  variant?: 'primary' | 'secondary'
  iconOnly?: boolean
}>(), {
  variant: 'secondary',
  iconOnly: false,
})

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {stationList, currentStationId, loaded, load, setActiveStation} = useStations()

onMounted(() => {
  if (!loaded.value) load()
})

const onCrossStation = computed(() => route.path.startsWith('/cross-station'))
const visible = computed(() => loaded.value && stationList.value.length > 0 && !onCrossStation.value)

function open() {
  if (!loaded.value || stationList.value.length === 0) return
  const stored = currentStationId.value
  const list = stationList.value
  if (stored && list.some(s => s.stationId === stored)) {
    router.push('/station/dashboard/overview')
    return
  }
  if (list.length === 1) {
    setActiveStation(list[0].stationId)
    router.push('/station/dashboard/overview')
    return
  }
  router.push('/cross-station')
}
</script>

<template>
  <PrimaryButton v-if="visible && variant === 'primary'" @click="open">
    <font-awesome-icon :icon="['fas', 'building']" class="h-4 w-4"/>
    <span v-if="!iconOnly" class="ml-1" :class="iconOnly ? '' : 'hidden sm:inline'">{{ t('header.stationPanel') }}</span>
  </PrimaryButton>
  <SecondaryButton v-else-if="visible" @click="open">
    <font-awesome-icon :icon="['fas', 'building']" class="h-4 w-4"/>
    <span v-if="!iconOnly" class="hidden sm:inline ml-1">{{ t('header.stationPanel') }}</span>
  </SecondaryButton>
</template>
