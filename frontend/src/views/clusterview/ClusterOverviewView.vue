/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {useSession} from '@/composables/useSession'
import {useCluster} from '@/composables/useCluster'
import type {Cluster} from '@/api/clusters'
import {clusters} from '@/api'

const {t} = useI18n()
const {sessionInfo} = useSession()
const {currentClusterId} = useCluster()

const cluster = ref<Cluster | null>(null)
const loading = ref(true)
const error = ref('')

const roleLabel = computed(() => {
  const type = sessionInfo.value?.clusterUserType
  return type ? t(`clusterOverview.role.${type}`) : t('clusterOverview.role.unknown')
})

onMounted(async () => {
  if (!currentClusterId.value) {
    loading.value = false
    return
  }
  try {
    cluster.value = await clusters.getActive()
  } catch {
    error.value = t('clusterOverview.loadFailed')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <ViewContent :title="t('clusterOverview.title')" :subtitle="t('clusterOverview.subtitle')">
    <Spinner v-if="loading"/>

    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

    <Alert v-else-if="!cluster" variant="info">{{ t('clusterOverview.noCluster') }}</Alert>

    <div v-else class="space-y-4">
      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ cluster.name }}</SectionHeader>
        <p v-if="cluster.description" class="text-(--text-muted)">{{ cluster.description }}</p>
        <p v-else class="text-(--text-muted) italic">{{ t('clusterOverview.noDescription') }}</p>
      </NeutralContainer>

      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('clusterOverview.yourRoleTitle') }}</SectionHeader>
        <p>{{ roleLabel }}</p>
        <p class="text-sm text-(--text-muted)">
          {{ t('clusterOverview.permissionCount', {count: sessionInfo?.clusterPermissions?.length ?? 0}) }}
        </p>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
