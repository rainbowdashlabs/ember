/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, watch} from 'vue'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import NavSidebar from '@/components/navigation/NavSidebar.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import MessagesButton from '@/components/layout/MessagesButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import QuickSearchPalette from '@/components/quicksearch/QuickSearchPalette.vue'
import ReportProblemButton from '@/components/feedback/ReportProblemButton.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearchShortcut} from '@/composables/useQuickSearchShortcut'
import {useSession} from '@/composables/useSession'
import {useCluster} from '@/composables/useCluster'
import {useClusterNav} from '@/composables/useClusterNav'
import {usePageHeader} from '@/composables/usePageHeader'
import {useClusterNotificationCount} from '@/composables/useClusterNotificationCount'
import {ClusterPermission} from '@/api/clusters'

const {loaded, sessionClusterId, load, hasClusterPermission} = useSession()
const {load: loadClusters, activeCluster, currentClusterId} = useCluster()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()
const {open: openQuickSearch} = useQuickSearchShortcut('cluster')
const {nodes} = useClusterNav()

/**
 * Whether the session we hold was answered for the cluster this shell is open on. Reaching the panel from
 * elsewhere in the app is an ordinary navigation, and the session loaded before it carries no cluster role
 * and no cluster permissions: without this the panel would show somebody as having no part in the cluster
 * they just opened, until the page was loaded again from scratch.
 */
const sessionCurrent = computed(() => loaded.value && sessionClusterId.value === currentClusterId.value)

const {refresh: refreshUnreadNotifications} = useClusterNotificationCount()

/** The number on the bell follows the association the shell is open on, once the reader may read its inbox. */
const mayReadInbox = computed(() => sessionCurrent.value && hasClusterPermission(ClusterPermission.USER))
watch(mayReadInbox, may => { if (may) void refreshUnreadNotifications() })

onMounted(() => {
  if (!sessionCurrent.value) {
    load()
  }
  void loadClusters()
  if (mayReadInbox.value) void refreshUnreadNotifications()
})
</script>

<template>
  <SidebarLayout :station-name="activeCluster?.name" :subtitle="pageSubtitle" :title="pageTitle" switcher>
    <template #sidebar="{ close }">
      <NavSidebar :nodes="nodes" @navigate="close"/>
    </template>

    <template #header>
      <QuickSearchTrigger scope="cluster" @open="openQuickSearch"/>
      <MessagesButton/>
      <AccountMenuButton/>
    </template>

    <template #dock>
      <ReportProblemButton/>
      <HelpCenterLink/>
    </template>

    <slot/>
    <QuickSearchPalette/>
  </SidebarLayout>
</template>
