/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import SmartStationButton from '@/components/layout/SmartStationButton.vue'
import AdminPanelButton from '@/components/layout/AdminPanelButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import {useSession} from '@/composables/useSession'
import {usePageHeader} from '@/composables/usePageHeader'

const {t} = useI18n()
const {loaded, load} = useSession()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()

onMounted(() => {
  if (!loaded.value) {
    load()
  }
})
</script>

<template>
  <SidebarLayout :subtitle="pageSubtitle" :title="pageTitle">
    <template #sidebar="{ close }">
      <SidebarLink :icon="['fas', 'image']" name="account-avatar" to="/account/avatar" @navigate="close">
        {{ t('sidebar.accountAvatar') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'palette']" name="account-theming" to="/account/theming" @navigate="close">
        {{ t('sidebar.accountTheming') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'list']" name="account-sessions" to="/account/sessions" @navigate="close">
        {{ t('sidebar.accountSessions') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'shield']" name="account-security" to="/account/security" @navigate="close">
        {{ t('sidebar.accountSecurity') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'shield-halved']" name="account-gdpr" to="/account/gdpr" @navigate="close">
        {{ t('sidebar.accountGdpr') }}
      </SidebarLink>
    </template>

    <template #header>
      <HelpCenterLink/>
      <AdminPanelButton/>
      <SmartStationButton/>
      <AccountMenuButton/>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
