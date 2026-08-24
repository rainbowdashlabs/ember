/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import SmartStationButton from '@/components/layout/SmartStationButton.vue'
import AdminPanelButton from '@/components/layout/AdminPanelButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import ClusterSwitcher from '@/components/navigation/ClusterSwitcher.vue'
import QuickSearchPalette from '@/components/quicksearch/QuickSearchPalette.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearchShortcut} from '@/composables/useQuickSearchShortcut'
import {useSession} from '@/composables/useSession'
import {useCluster} from '@/composables/useCluster'
import {usePageHeader} from '@/composables/usePageHeader'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {loaded, sessionClusterId, load, hasClusterPermission} = useSession()
const {load: loadClusters, activeCluster, currentClusterId} = useCluster()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()
const {open: openQuickSearch} = useQuickSearchShortcut('cluster')

/**
 * Whether the session we hold was answered for the cluster this shell is open on. Reaching the panel from
 * elsewhere in the app is an ordinary navigation, and the session loaded before it carries no cluster role
 * and no cluster permissions: without this the panel would show somebody as having no part in the cluster
 * they just opened, until the page was loaded again from scratch.
 */
const sessionCurrent = computed(() => loaded.value && sessionClusterId.value === currentClusterId.value)

onMounted(() => {
  if (!sessionCurrent.value) {
    load()
  }
  void loadClusters()
})
</script>

<template>
  <SidebarLayout :station-name="activeCluster?.name" :subtitle="pageSubtitle" :title="pageTitle">
    <template #sidebar="{ close }">
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
            :icon="['fas', 'layer-group']"
            name="cluster-station-groups"
            to="/cluster/stations/groups"
            @navigate="close"
        >
          {{ t('clusterSidebar.stationGroups') }}
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
          v-if="hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_READ)"
          :icon="['fas', 'user-group']"
          :label="t('clusterSidebar.team')"
          prefix="/cluster/team"
      >
        <SidebarLink :icon="['fas', 'users']" name="cluster-team" to="/cluster/team" @navigate="close">
          {{ t('clusterSidebar.teamList') }}
        </SidebarLink>
        <SidebarLink
            :icon="['fas', 'user-group']"
            name="cluster-team-groups"
            to="/cluster/team/groups"
            @navigate="close"
        >
          {{ t('clusterSidebar.memberGroups') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup
          v-if="hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_MANAGER)"
          :icon="['fas', 'users']"
          :label="t('clusterSidebar.members')"
          prefix="/cluster/members"
      >
        <SidebarLink
            :icon="['fas', 'users-gear']"
            name="cluster-members"
            to="/cluster/members"
            @navigate="close"
        >
          {{ t('clusterSidebar.memberList') }}
        </SidebarLink>
        <SidebarLink
            :icon="['fas', 'id-card']"
            name="cluster-fields"
            to="/cluster/members/fields"
            @navigate="close"
        >
          {{ t('clusterSidebar.fieldList') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup
          v-if="hasClusterPermission(ClusterPermission.USER)"
          :icon="['fas', 'newspaper']"
          :label="t('clusterSidebar.content')"
          prefix="/cluster/content"
      >
        <SidebarLink :icon="['fas', 'book']" name="cluster-knowledge" to="/cluster/knowledge" @navigate="close">
          {{ t('clusterSidebar.knowledge') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'newspaper']" name="cluster-news" to="/cluster/news" @navigate="close">
          {{ t('clusterSidebar.news') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'calendar']" name="cluster-events" to="/cluster/events" @navigate="close">
          {{ t('clusterSidebar.events') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup
          v-if="hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_READ)"
          :icon="['fas', 'boxes-stacked']"
          :label="t('clusterSidebar.inventory')"
          prefix="/cluster/inventory"
      >
        <SidebarLink
            :icon="['fas', 'boxes-stacked']"
            name="cluster-inventory"
            to="/cluster/inventory"
            @navigate="close"
        >
          {{ t('clusterSidebar.stock') }}
        </SidebarLink>
        <SidebarLink
            :icon="['fas', 'right-left']"
            name="cluster-movements"
            to="/cluster/inventory/movements"
            @navigate="close"
        >
          {{ t('clusterSidebar.movements') }}
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

    </template>

    <template #header>
      <QuickSearchTrigger scope="cluster" @open="openQuickSearch"/>
      <HelpCenterLink/>
      <AdminPanelButton/>
      <SmartStationButton/>
      <AccountMenuButton/>
    </template>

    <template #footer>
      <ClusterSwitcher/>
    </template>

    <slot/>
    <QuickSearchPalette/>
  </SidebarLayout>
</template>
