/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ResetPanel from '@/views/adminview/admintwofactorview/ResetPanel.vue'
import AuditPanel from '@/views/adminview/admintwofactorview/AuditPanel.vue'

const {t} = useI18n()
const auditRef = ref<InstanceType<typeof AuditPanel> | null>(null)

function onResetPerformed() {
  auditRef.value?.reload()
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <SectionHeader>{{ t('twoFactor.admin.instanceTitle') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('twoFactor.admin.instanceHint') }}</MutedText>
      </div>

      <ResetPanel @reset-performed="onResetPerformed"/>
      <AuditPanel ref="auditRef"/>
    </div>
  </ViewContent>
</template>
