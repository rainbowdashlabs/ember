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
