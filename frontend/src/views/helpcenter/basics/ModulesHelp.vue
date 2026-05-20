/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()

const modules = [
  {key: 'attendance', icon: ['fas', 'clipboard-user']},
  {key: 'events', icon: ['fas', 'calendar-days']},
  {key: 'inventory', icon: ['fas', 'boxes-stacked']},
  {key: 'news', icon: ['fas', 'newspaper']},
  {key: 'forms', icon: ['fas', 'square-poll-vertical']},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.modules.title')" :subtitle="t('helpCenter.basics.modules.subtitle')">
    <HelpSection :title="t('helpCenter.basics.modules.whatAre')">
      <p>{{ t('helpCenter.basics.modules.whatAreText') }}</p>
      <p>{{ t('helpCenter.basics.modules.whatAreText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.modules.available')">
      <div class="space-y-2">
        <NeutralContainer v-for="m in modules" :key="m.key" class="flex items-center justify-between p-3">
          <div class="flex items-center gap-3">
            <font-awesome-icon :icon="m.icon" class="h-4 w-4 text-primary shrink-0"/>
            <div>
              <p class="font-semibold text-sm">{{ t(`helpCenter.basics.modules.module.${m.key}.name`) }}</p>
              <p class="text-xs text-(--text-muted)">{{ t(`helpCenter.basics.modules.module.${m.key}.desc`) }}</p>
            </div>
          </div>
          <SuccessBadge>{{ t('helpCenter.basics.modules.active') }}</SuccessBadge>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.modules.toggle')">
      <p>{{ t('helpCenter.basics.modules.toggleText') }}</p>
      <!-- Dummy toggle example -->
      <NeutralContainer class="p-4 mt-3 space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'clipboard-user']" class="h-4 w-4 text-primary"/>
            <span class="text-sm font-medium">{{ t('helpCenter.basics.modules.module.attendance.name') }}</span>
          </div>
          <ToggleInput :model-value="true"/>
        </div>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'square-poll-vertical']" class="h-4 w-4 text-primary"/>
            <span class="text-sm font-medium">{{ t('helpCenter.basics.modules.module.forms.name') }}</span>
          </div>
          <ToggleInput :model-value="false"/>
        </div>
      </NeutralContainer>
      <p class="mt-3">{{ t('helpCenter.basics.modules.toggleText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.modules.navigation')">
      <p>{{ t('helpCenter.basics.modules.navigationText') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.basics.modules.nav1') }}</li>
        <li>{{ t('helpCenter.basics.modules.nav2') }}</li>
        <li>{{ t('helpCenter.basics.modules.nav3') }}</li>
        <li>{{ t('helpCenter.basics.modules.nav4') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.modules.notifications')">
      <p>{{ t('helpCenter.basics.modules.notificationsText') }}</p>
      <p>{{ t('helpCenter.basics.modules.notificationsText2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.modules.tip') }}</HelpTip>
  </HelpArticle>
</template>
