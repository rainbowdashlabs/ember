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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import HelpCenterSidebar from '@/views/helpcenterstationview/HelpCenterSidebar.vue'
import {claimPageHeader} from '@/composables/usePageHeader'

const {t, te} = useI18n()
const route = useRoute()

/**
 * The help page's own title when it has one, otherwise the title of the page it documents with
 * the help-center suffix, so an article always says what it is about.
 */
const pageTitle = computed(() => {
  const name = (route.name as string)?.replace('help-', '') ?? ''
  const key = `pages.${name}.title`
  const helpKey = `helpCenter.pages.${route.name as string}.title`
  if (te(helpKey)) return t(helpKey)
  return te(key) ? `${t(key)} - ${t('helpCenter.link')}` : t('helpCenter.link')
})

const pageSubtitle = computed(() => t('helpCenter.title'))

const {set: setPageHeader} = claimPageHeader()
watch(
    () => [pageTitle.value, pageSubtitle.value] as const,
    ([titleValue, subtitleValue]) => setPageHeader(titleValue, subtitleValue),
    {immediate: true},
)
</script>

<template>
  <SidebarLayout :subtitle="pageSubtitle" :title="pageTitle" :station-name="t('helpCenter.title')" :collapsible="false">
    <template #sidebar="{ close }">
      <HelpCenterSidebar :close="close"/>
    </template>

    <template #header>
      <router-link to="/helpcenter/admin/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('helpCenter.adminHelp') }}</span>
        </SecondaryButton>
      </router-link>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
