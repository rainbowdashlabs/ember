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
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {Cluster} from '@/api/clusters'
import type {ClusterMemberSummary} from '@/api/clusterMembers'
import type {PermissionNode} from '@/api/data'
import {highestOf} from '@/api/data'
import {clusters, clusterMembers, data} from '@/api'

const {t} = useI18n()
const {sessionInfo} = useSession()
const {currentClusterId} = useCluster()

const cluster = ref<Cluster | null>(null)
const administrators = ref<ClusterMemberSummary[]>([])
const loading = ref(true)
const error = ref('')

const hierarchy = ref<PermissionNode[]>([])

/**
 * What the reader may do here, named rather than counted.
 *
 * Only the rights nothing else they hold already carries: somebody trusted with the whole of member
 * management reads as holding that, not as holding it and the four smaller rights it is made of. The two
 * that only say "you are here" are left out for the same reason, since they are true of everybody who can
 * read this page at all.
 */
const permissions = computed(() =>
    highestOf(
        (sessionInfo.value?.clusterPermissions ?? []).filter(p => p !== 'USER' && p !== 'LOGIN'),
        hierarchy.value,
    ).sort())

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
    administrators.value = await clusterMembers.listAdministrators().catch(() => [])
    hierarchy.value = await data.getClusterPermissionHierarchy().catch(() => [])
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

      <NeutralContainer class="space-y-3">
        <SectionHeader>{{ t('clusterOverview.yourRoleTitle') }}</SectionHeader>
        <p>{{ roleLabel }}</p>

        <div v-if="permissions.length" class="space-y-1">
          <p class="text-sm text-(--text-muted)">{{ t('clusterOverview.permissionsTitle') }}</p>
          <div class="flex flex-wrap gap-1">
            <PrimaryBadge v-for="permission in permissions" :key="permission">
              {{ t(`permissions.${permission}.label`) }}
            </PrimaryBadge>
          </div>
        </div>
        <p v-else class="text-sm text-(--text-muted)">{{ t('clusterOverview.permissionsNone') }}</p>
      </NeutralContainer>

      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('clusterOverview.administratorsTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('clusterOverview.administratorsHint') }}</p>
        <div v-if="administrators.length" class="flex flex-wrap gap-1">
          <SecondaryBadge v-for="person in administrators" :key="person.id">
            {{ person.name || person.email }}
          </SecondaryBadge>
        </div>
        <p v-else class="text-(--text-muted) italic">{{ t('clusterOverview.administratorsNone') }}</p>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
