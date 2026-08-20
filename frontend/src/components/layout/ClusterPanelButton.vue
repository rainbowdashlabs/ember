/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {useCluster} from '@/composables/useCluster'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

withDefaults(defineProps<{
  variant?: 'primary' | 'secondary'
}>(), {
  variant: 'secondary',
})

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {clusterList, currentClusterId, loaded, load, hasClusters, setActiveCluster} = useCluster()

onMounted(() => {
  if (!loaded.value) void load()
})

const onClusterPanel = computed(() => route.path.startsWith('/cluster'))
const visible = computed(() => loaded.value && hasClusters.value && !onClusterPanel.value)

function open() {
  const stored = currentClusterId.value
  // Somebody who belongs to exactly one cluster never gets asked which one they meant
  if (!stored || !clusterList.value.some(c => c.uid === stored)) {
    const [only] = clusterList.value
    if (only) setActiveCluster(only.uid)
  }
  router.push('/cluster')
}
</script>

<template>
  <PrimaryButton v-if="visible && variant === 'primary'" @click="open">
    <font-awesome-icon :icon="['fas', 'sitemap']" class="h-4 w-4"/>
    <span class="hidden sm:inline ml-1">{{ t('header.clusterPanel') }}</span>
  </PrimaryButton>
  <SecondaryButton v-else-if="visible" @click="open">
    <font-awesome-icon :icon="['fas', 'sitemap']" class="h-4 w-4"/>
    <span class="hidden sm:inline ml-1">{{ t('header.clusterPanel') }}</span>
  </SecondaryButton>
</template>
