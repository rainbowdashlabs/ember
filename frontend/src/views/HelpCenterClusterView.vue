/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {claimPageHeader} from '@/composables/usePageHeader'

const {t, te} = useI18n()
const route = useRoute()

const pageTitle = computed(() => {
  const name = (route.name as string)?.replace('help-', '') ?? ''
  const key = `pages.${name}.title`
  return te(key) ? `${t(key)} - ${t('helpCenter.link')}` : t('helpCenter.clusterHelp')
})

const {set: setPageHeader} = claimPageHeader()
watch(
    () => [pageTitle.value, t('helpCenter.title')] as const,
    ([titleValue, subtitleValue]) => setPageHeader(titleValue, subtitleValue),
    {immediate: true},
)
</script>

<template>
  <SidebarLayout :collapsible="false" :station-name="t('helpCenter.clusterHelp')" :subtitle="t('helpCenter.title')"
                 :title="pageTitle">
    <template #sidebar="{ close }">
      <SidebarGroup :icon="['fas', 'sitemap']" :label="t('clusterSidebar.cluster')">
        <SidebarLink :icon="['fas', 'house']" name="help-cluster-overview" to="/helpcenter/cluster" @navigate="close">
          {{ t('clusterSidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'sliders']" name="help-cluster-settings" to="/helpcenter/cluster/settings"
                     @navigate="close">
          {{ t('clusterSidebar.settings') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'building']" :label="t('clusterSidebar.stations')">
        <SidebarLink :icon="['fas', 'building']" name="help-cluster-stations" to="/helpcenter/cluster/stations"
                     @navigate="close">
          {{ t('clusterSidebar.stationList') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-cluster-applications"
                     to="/helpcenter/cluster/applications" @navigate="close">
          {{ t('clusterSidebar.applications') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'users']" :label="t('clusterSidebar.members')">
        <SidebarLink :icon="['fas', 'users']" name="help-cluster-members"
                     to="/helpcenter/cluster/members" @navigate="close">
          {{ t('clusterSidebar.memberList') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'user-group']" name="help-cluster-member-groups"
                     to="/helpcenter/cluster/members/groups" @navigate="close">
          {{ t('clusterSidebar.memberGroups') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'users-gear']" name="help-cluster-member-management"
                     to="/helpcenter/cluster/members/manage" @navigate="close">
          {{ t('clusterSidebar.memberManagement') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'id-card']" name="help-cluster-fields"
                     to="/helpcenter/cluster/members/fields" @navigate="close">
          {{ t('clusterSidebar.fieldList') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'book']" :label="t('clusterSidebar.knowledge')" to="/helpcenter/cluster/knowledge"
                    name="help-cluster-knowledge" @navigate="close"/>
      <SidebarGroup :icon="['fas', 'newspaper']" :label="t('clusterSidebar.news')" to="/helpcenter/cluster/news"
                    name="help-cluster-news" @navigate="close"/>
      <SidebarGroup :icon="['fas', 'calendar']" :label="t('clusterSidebar.events')" to="/helpcenter/cluster/events"
                    name="help-cluster-events" @navigate="close"/>

      <SidebarGroup :icon="['fas', 'boxes-stacked']" :label="t('clusterSidebar.inventory')">
        <SidebarLink :icon="['fas', 'boxes-stacked']" name="help-cluster-inventory"
                     to="/helpcenter/cluster/inventory" @navigate="close">
          {{ t('clusterSidebar.stock') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'right-left']" name="help-cluster-movements"
                     to="/helpcenter/cluster/inventory/movements" @navigate="close">
          {{ t('clusterSidebar.movements') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gear']" :label="t('clusterSidebar.governance')"
                    group-key="help-cluster-governance">
        <SidebarLink :icon="['fas', 'puzzle-piece']" name="help-cluster-modules"
                     to="/helpcenter/cluster/modules" @navigate="close">
          {{ t('clusterSidebar.modules') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'palette']" name="help-cluster-look-and-feel"
                     to="/helpcenter/cluster/look-and-feel" @navigate="close">
          {{ t('clusterSidebar.lookAndFeel') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'hard-drive']" name="help-cluster-storage"
                     to="/helpcenter/cluster/storage" @navigate="close">
          {{ t('clusterSidebar.storage') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <slot/>
  </SidebarLayout>
</template>
