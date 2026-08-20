/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import StationSwitcher from '@/components/navigation/StationSwitcher.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import ClusterPanelButton from '@/components/layout/ClusterPanelButton.vue'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearch} from '@/composables/useQuickSearch'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {isAdmin} = useSession()
const {open: openQuickSearch} = useQuickSearch()
</script>

<template>
  <div class="hidden lg:flex"><StationSwitcher/></div>
  <QuickSearchTrigger scope="station" @open="openQuickSearch"/>
  <HelpCenterLink/>

  <router-link v-if="isAdmin()" to="/admin/dashboard/overview">
    <SecondaryButton>
      <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
      <span class="hidden sm:inline ml-1">{{ t('header.adminPanel') }}</span>
    </SecondaryButton>
  </router-link>

  <ClusterPanelButton/>

  <AccountMenuButton/>
</template>
