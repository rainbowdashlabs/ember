/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import MembersSidebarGroup from '@/views/helpcenterstationview/MembersSidebarGroup.vue'
import InventorySidebarGroup from '@/views/helpcenterstationview/InventorySidebarGroup.vue'
import ModuleSidebarGroups from '@/views/helpcenterstationview/ModuleSidebarGroups.vue'
import {useHelpSearch} from '@/composables/useHelpSearch'
import {STEP_ORDER} from '@/views/stationview/setup/steps'

/**
 * The help center's station navigation: a search box that replaces the tree with its results
 * while a query is active, and otherwise the tree itself - the always-present areas here, the
 * optional modules in {@link ModuleSidebarGroups}.
 */
const props = defineProps<{
  close: () => void
}>()

const {t} = useI18n()
const router = useRouter()
const {query: searchQuery, results: searchResults, isSearching, clearSearch} = useHelpSearch()

function navigateToResult(path: string) {
  router.push(path)
  clearSearch()
  props.close()
}

/**
 * Wraps the matched span of a result snippet in a highlight. The surrounding text is escaped
 * here because the result is rendered as HTML, and article content is not trusted markup.
 */
function highlightSnippet(result: (typeof searchResults.value)[number]): string {
  const s = result.snippet
  const before = escapeHtml(s.substring(0, result.matchStart))
  const match = escapeHtml(s.substring(result.matchStart, result.matchEnd))
  const after = escapeHtml(s.substring(result.matchEnd))
  return `${before}<mark class="bg-amber-300 dark:bg-amber-600 text-inherit rounded-xs px-0.5">${match}</mark>${after}`
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<template>
  <div class="px-2 pb-3">
    <SearchInput v-model="searchQuery" :placeholder="t('helpCenter.search')"/>
  </div>

  <template v-if="isSearching">
    <div v-if="searchResults.length === 0" class="px-3 py-4 text-sm text-[var(--text-muted)]">
      {{ t('helpCenter.noSearchResults') }}
    </div>
    <div v-else class="flex flex-col gap-1 px-2 pb-3">
      <BaseButton v-for="result in searchResults" :key="result.entry.route"
                  class="!text-left !block !py-2 hover:bg-[var(--bg-hover)]"
                  data-testid="help-search-result"
                  full-width
                  @click="navigateToResult(result.entry.path)">
        <div class="text-sm font-medium text-[var(--text)]">{{ result.entry.title }}</div>
        <div class="text-xs text-[var(--text-muted)] mb-1">{{ result.entry.section }}</div>
        <div class="text-xs text-[var(--text-muted)] leading-relaxed break-words"
             v-html="highlightSnippet(result)"/>
      </BaseButton>
    </div>
  </template>

  <template v-else>
    <SidebarGroup :icon="['fas', 'book']" :label="t('helpCenter.basics.sidebar')"
                  to="/helpcenter/station/basics" name="help-welcome" @navigate="close">
      <SidebarLink :icon="['fas', 'circle-info']" name="help-basics-overview"
                   to="/helpcenter/station/basics/overview" @navigate="close">
        {{ t('helpCenter.basics.sidebarOverview') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'shield']" name="help-basics-permissions"
                   to="/helpcenter/station/basics/permissions" @navigate="close">
        {{ t('helpCenter.basics.sidebarPermissions') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'puzzle-piece']" name="help-basics-modules"
                   to="/helpcenter/station/basics/modules" @navigate="close">
        {{ t('helpCenter.basics.sidebarModules') }}
      </SidebarLink>
      <SidebarExpandableLink :icon="['fas', 'server']" name="help-basics-hosting"
                             to="/helpcenter/station/basics/hosting"
                             prefix="/helpcenter/station/basics/hosting" @navigate="close">
        <template #label>{{ t('helpCenter.basics.sidebarHosting') }}</template>
        <SidebarLink :icon="['fas', 'sliders']" name="help-basics-hosting-configuration"
                     to="/helpcenter/station/basics/hosting/configuration" @navigate="close">
          {{ t('helpCenter.basics.sidebarHostingConfiguration') }}
        </SidebarLink>
      </SidebarExpandableLink>
      <SidebarLink :icon="['fas', 'arrow-right-arrow-left']" name="help-basics-federation"
                   to="/helpcenter/station/basics/federation" @navigate="close">
        {{ t('helpCenter.basics.sidebarFederation') }}
      </SidebarLink>
    </SidebarGroup>

    <SidebarGroup :icon="['fas', 'rocket']" :label="t('setup.headerTitle')" prefix="/helpcenter/station/setup"
                  to="/helpcenter/station/setup" name="help-setup-module-overview" @navigate="close">
      <SidebarLink v-for="step in STEP_ORDER" :key="step" :icon="['fas', 'circle-dot']"
                   :name="`help-station-setup-${step}`" :to="`/helpcenter/station/setup/${step}`" @navigate="close">
        {{ t(`setup.steps.${step}.label`) }}
      </SidebarLink>
    </SidebarGroup>

    <SidebarGroup :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')"
                  to="/helpcenter/station/dashboard" name="help-dashboard-module-overview" @navigate="close">
      <SidebarLink :icon="['fas', 'house']" name="help-dashboard-overview"
                   to="/helpcenter/station/dashboard/overview" @navigate="close">
        {{ t('sidebar.overview') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'chart-line']" name="help-dashboard-statistics"
                   to="/helpcenter/station/dashboard/statistics" @navigate="close">
        {{ t('sidebar.statistics') }}
      </SidebarLink>
    </SidebarGroup>

    <SidebarGroup :icon="['fas', 'clipboard-check']" :label="t('sidebar.requirements')"
                  to="/helpcenter/station/requirements" name="help-station-requirements" @navigate="close"/>

    <SidebarGroup :icon="['fas', 'newspaper']" :label="t('sidebar.news')"
                  to="/helpcenter/station/news" name="help-news-module-overview" @navigate="close">
      <SidebarLink :icon="['fas', 'pen']" name="help-news-create" to="/helpcenter/station/news/create" @navigate="close">
        {{ t('sidebar.newsEdit') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'eye']" name="help-news-detail" to="/helpcenter/station/news/0" @navigate="close">
        {{ t('helpCenter.newsDetail.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'share-nodes']" name="help-federated-news-detail"
                   to="/helpcenter/station/federation/news/0/1" @navigate="close">
        {{ t('helpCenter.federatedNewsDetail.title') }}
      </SidebarLink>
    </SidebarGroup>

    <SidebarGroup :icon="['fas', 'user']" :label="t('sidebar.profile')"
                  to="/helpcenter/station/profile" name="help-profile-module-overview" @navigate="close">
      <SidebarLink :icon="['fas', 'calendar-days']" name="help-profile-absences"
                   to="/helpcenter/station/profile/absences" @navigate="close">
        {{ t('sidebar.absences') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'users']" name="help-profile-managed"
                   to="/helpcenter/station/profile/managed" @navigate="close">
        {{ t('sidebar.managedProfiles') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'boxes-stacked']" name="help-profile-inventory"
                   to="/helpcenter/station/profile/inventory" @navigate="close">
        {{ t('sidebar.myInventory') }}
      </SidebarLink>
      <SidebarExpandableLink :icon="['fas', 'gear']" name="help-profile-settings"
                             to="/helpcenter/station/profile/settings" prefix="/helpcenter/station/profile/settings" @navigate="close">
        <template #label>{{ t('sidebar.settings') }}</template>
        <SidebarLink :icon="['fas', 'palette']" name="help-profile-theming"
                     to="/helpcenter/station/profile/settings/theming" @navigate="close">
          {{ t('sidebar.theming') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'desktop']" name="help-profile-sessions"
                     to="/helpcenter/station/profile/settings/sessions" @navigate="close">
          {{ t('sidebar.sessions') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'bell']" name="help-profile-notifications"
                     to="/helpcenter/station/profile/settings/notifications" @navigate="close">
          {{ t('sidebar.notifications') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'shield']" name="help-profile-security"
                     to="/helpcenter/station/profile/settings/security" @navigate="close">
          {{ t('sidebar.security') }}
        </SidebarLink>
      </SidebarExpandableLink>
      <SidebarLink :icon="['fas', 'palette']" name="help-profile-theme"
                   to="/helpcenter/station/profile/theme" @navigate="close">
        {{ t('helpCenter.themeUser.sidebarLabel') }}
      </SidebarLink>
    </SidebarGroup>

    <SidebarGroup :icon="['fas', 'gears']" :label="t('sidebar.station')"
                  to="/helpcenter/station/manage" name="help-manage-module-overview" @navigate="close">
      <SidebarLink :icon="['fas', 'palette']" name="help-station-theme-manage"
                   to="/helpcenter/station/manage/theme" @navigate="close">
        {{ t('helpCenter.themeManage.sidebarLabel') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'sitemap']" name="help-station-manage-cluster"
                   to="/helpcenter/station/manage/cluster" @navigate="close">
        {{ t('sidebar.stationCluster') }}
      </SidebarLink>
      <SidebarExpandableLink :icon="['fas', 'arrow-right-arrow-left']" name="help-station-federation"
                             to="/helpcenter/station/federate" @navigate="close">
        <template #label>{{ t('helpCenter.federation.sidebarLabel') }}</template>
        <SidebarLink :icon="['fas', 'sliders']" name="help-station-federation-partner"
                     to="/helpcenter/station/federate/0" @navigate="close">
          {{ t('helpCenter.federationPartner.sidebarLabel') }}
        </SidebarLink>
      </SidebarExpandableLink>
    </SidebarGroup>

    <SidebarGroup :icon="['fas', 'chart-line']" :label="t('sidebar.monitoring')"
                  to="/helpcenter/station/monitoring/traffic" name="help-station-traffic" @navigate="close">
      <SidebarLink :icon="['fas', 'chart-pie']" name="help-station-insights"
                   to="/helpcenter/station/monitoring/insights" @navigate="close">
        {{ t('sidebar.stationInsights') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'rss']" name="help-station-feeds"
                   to="/helpcenter/station/monitoring/feeds" @navigate="close">
        {{ t('sidebar.stationFeeds') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'hard-drive']" name="help-station-storage"
                   to="/helpcenter/station/monitoring/storage" @navigate="close">
        {{ t('sidebar.storage') }}
      </SidebarLink>
    </SidebarGroup>

    <MembersSidebarGroup :close="close"/>

    <InventorySidebarGroup :close="close"/>

    <ModuleSidebarGroups :close="close"/>
  </template>
</template>
