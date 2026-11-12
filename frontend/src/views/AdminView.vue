/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {useMonitoringCounts} from '@/composables/useMonitoringCounts'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import NavSidebar from '@/components/navigation/NavSidebar.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import MessagesButton from '@/components/layout/MessagesButton.vue'
import {useSession} from '@/composables/useSession'
import {useAdminNav} from '@/composables/useAdminNav'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import QuickSearchPalette from '@/components/quicksearch/QuickSearchPalette.vue'
import OnboardingGuide from '@/components/onboarding/OnboardingGuide.vue'
import OnboardingResumeButton from '@/components/onboarding/OnboardingResumeButton.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearchShortcut} from '@/composables/useQuickSearchShortcut'
import {usePageHeader} from '@/composables/usePageHeader'

/**
 * The instance administration shell and its sidebar.
 *
 * <p>The beacon settings entry always shows, since setting a beacon up is how an instance becomes
 * one; what a beacon gathers shows only once it is active, rather than as empty pages everywhere else.
 * The inspector tools show in development builds only. The monitoring counts behind the sidebar's
 * badges are read again on every move to another page of the administration.
 */
const {t} = useI18n()
const {loaded, load} = useSession()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()
const {nodes} = useAdminNav()

const {open: openQuickSearch} = useQuickSearchShortcut('admin')

const {refresh: refreshCounts} = useMonitoringCounts()
const route = useRoute()

onMounted(() => {
  if (!loaded.value) {
    load()
  }
  refreshCounts()
})

watch(() => route.path, () => { void refreshCounts() })

</script>

<template>
  <SidebarLayout :subtitle="pageSubtitle" :title="pageTitle" :station-name="t('placeSwitcher.administration')" switcher>
    <template #sidebar="{ close }">
      <NavSidebar :nodes="nodes" @navigate="close"/>
    </template>

    <template #header>
      <QuickSearchTrigger scope="admin" @open="openQuickSearch"/>
      <MessagesButton/>
      <AccountMenuButton/>
    </template>

    <template #dock>
      <HelpCenterLink/>
      <OnboardingResumeButton level="INSTANCE"/>
    </template>

    <slot><RouterView/></slot>
    <OnboardingGuide/>
    <QuickSearchPalette/>
  </SidebarLayout>
</template>
