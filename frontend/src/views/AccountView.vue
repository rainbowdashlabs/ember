/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import NavSidebar from '@/components/navigation/NavSidebar.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import {useSession} from '@/composables/useSession'
import {useAccountNav} from '@/composables/useAccountNav'
import {usePageHeader} from '@/composables/usePageHeader'

const {t} = useI18n()
const {loaded, load} = useSession()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()
const {nodes} = useAccountNav()

onMounted(() => {
  if (!loaded.value) {
    load()
  }
})
</script>

<template>
  <SidebarLayout :subtitle="pageSubtitle" :title="pageTitle" :station-name="t('placeSwitcher.account')" switcher>
    <template #sidebar="{ close }">
      <NavSidebar :nodes="nodes" @navigate="close"/>
    </template>

    <template #header>
      <AccountMenuButton/>
    </template>

    <template #dock>
      <HelpCenterLink/>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
