/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {SidebarMode} from '@/api/generated/schema'
import {useSidebarMode} from '@/composables/useSidebarMode'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Whether the sidebar shows every section at once, as a tree opened in place, rather than one section at
 * a time. The choice is saved with the account the moment it is switched and holds on every device.
 */
const {t} = useI18n()
const {tree, choose} = useSidebarMode()

const saving = useAsyncAction((all: boolean) => choose(all ? SidebarMode.TREE : SidebarMode.SECTIONS))
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('sidebarMode.title') }}</SubHeader>
    <ToggleSetting :model-value="tree" :label="t('sidebarMode.tree')" :hint="t('sidebarMode.treeHint')"
                   :disabled="saving.running.value" data-testid="sidebar-mode-tree"
                   @update:model-value="all => saving.run(all)"/>
    <FailureAlert :failure="saving.failure.value"/>
  </NeutralContainer>
</template>
