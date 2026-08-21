/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import SmartStationButton from '@/components/layout/SmartStationButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import ClusterSwitcher from '@/components/navigation/ClusterSwitcher.vue'
import {useSession} from '@/composables/useSession'
import {useCluster} from '@/composables/useCluster'
import {usePageHeader} from '@/composables/usePageHeader'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {loaded, load, hasClusterPermission} = useSession()
const {load: loadClusters, activeCluster} = useCluster()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()

onMounted(() => {
  if (!loaded.value) {
    load()
  }
  void loadClusters()
})
</script>

<template>
  <SidebarLayout :station-name="activeCluster?.name" :subtitle="pageSubtitle" :title="pageTitle">
    <template #sidebar="{ close }">
      <ClusterSwitcher/>

      <SidebarGroup :icon="['fas', 'sitemap']" :label="t('clusterSidebar.cluster')" prefix="/cluster">
        <SidebarLink :icon="['fas', 'house']" name="cluster-overview" to="/cluster" @navigate="close">
          {{ t('clusterSidebar.overview') }}
        </SidebarLink>
        <SidebarLink
            v-if="hasClusterPermission(ClusterPermission.CLUSTER_GENERAL)"
            :icon="['fas', 'sliders']"
            name="cluster-settings"
            to="/cluster/settings"
            @navigate="close"
        >
          {{ t('clusterSidebar.settings') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup
          v-if="hasClusterPermission(ClusterPermission.USER)"
          :icon="['fas', 'building']"
          :label="t('clusterSidebar.stations')"
          prefix="/cluster/stations"
      >
        <SidebarLink :icon="['fas', 'building']" name="cluster-stations" to="/cluster/stations" @navigate="close">
          {{ t('clusterSidebar.stationList') }}
        </SidebarLink>
        <SidebarLink
            v-if="hasClusterPermission(ClusterPermission.CLUSTER_STATIONS)"
            :icon="['fas', 'clipboard-list']"
            name="cluster-applications"
            to="/cluster/applications"
            @navigate="close"
        >
          {{ t('clusterSidebar.applications') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup
          v-if="hasClusterPermission(ClusterPermission.CLUSTER_MODULES)
              || hasClusterPermission(ClusterPermission.CLUSTER_LOOK_AND_FEEL)
              || hasClusterPermission(ClusterPermission.CLUSTER_STORAGE)"
          :icon="['fas', 'gear']"
          :label="t('clusterSidebar.governance')"
          prefix="/cluster/governance"
      >
        <SidebarLink
            v-if="hasClusterPermission(ClusterPermission.CLUSTER_MODULES)"
            :icon="['fas', 'puzzle-piece']"
            name="cluster-modules"
            to="/cluster/modules"
            @navigate="close"
        >
          {{ t('clusterSidebar.modules') }}
        </SidebarLink>
        <SidebarLink
            v-if="hasClusterPermission(ClusterPermission.CLUSTER_LOOK_AND_FEEL)"
            :icon="['fas', 'palette']"
            name="cluster-look-and-feel"
            to="/cluster/look-and-feel"
            @navigate="close"
        >
          {{ t('clusterSidebar.lookAndFeel') }}
        </SidebarLink>
        <SidebarLink
            v-if="hasClusterPermission(ClusterPermission.CLUSTER_STORAGE)"
            :icon="['fas', 'hard-drive']"
            name="cluster-storage"
            to="/cluster/storage"
            @navigate="close"
        >
          {{ t('clusterSidebar.storage') }}
        </SidebarLink>
      </SidebarGroup>

      <div class="mt-auto flex flex-col gap-2 pt-4">
        <SmartStationButton/>
        <div class="flex items-center gap-2">
          <AccountMenuButton/>
          <HelpCenterLink/>
        </div>
      </div>
    </template>

    <slot/>
  </SidebarLayout>
</template>
