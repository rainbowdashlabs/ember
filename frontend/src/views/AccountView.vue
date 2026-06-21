/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import {useSession} from '@/composables/useSession'

const {t, te} = useI18n()
const route = useRoute()
const {loaded, load} = useSession()

onMounted(() => {
  if (!loaded.value) {
    load()
  }
})

const pageTitle = computed(() => {
  const key = `pages.${route.name as string}.title`
  return te(key) ? t(key) : ''
})

const pageSubtitle = computed(() => {
  const key = `pages.${route.name as string}.subtitle`
  return te(key) ? t(key) : ''
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

      <router-link to="/station/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'building']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('header.stationPanel') }}</span>
        </SecondaryButton>
      </router-link>

      <AccountMenuButton/>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
