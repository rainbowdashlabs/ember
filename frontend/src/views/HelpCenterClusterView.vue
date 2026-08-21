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
      <SidebarGroup :icon="['fas', 'sitemap']" :label="t('clusterSidebar.cluster')" prefix="/helpcenter/cluster">
        <SidebarLink :icon="['fas', 'house']" name="help-cluster-overview" to="/helpcenter/cluster" @navigate="close">
          {{ t('clusterSidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'sliders']" name="help-cluster-settings" to="/helpcenter/cluster/settings"
                     @navigate="close">
          {{ t('clusterSidebar.settings') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'building']" :label="t('clusterSidebar.stations')"
                    prefix="/helpcenter/cluster/stations">
        <SidebarLink :icon="['fas', 'building']" name="help-cluster-stations" to="/helpcenter/cluster/stations"
                     @navigate="close">
          {{ t('clusterSidebar.stationList') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-cluster-applications"
                     to="/helpcenter/cluster/applications" @navigate="close">
          {{ t('clusterSidebar.applications') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <slot/>
  </SidebarLayout>
</template>
