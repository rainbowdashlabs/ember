/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MailDashboard from '@/components/mail/MailDashboard.vue'
import {getInstanceMailDashboard} from '@/api/mailProviders'

const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.admin-mail-log.title')" :subtitle="t('pages.admin-mail-log.subtitle')">
    <MailDashboard :load="getInstanceMailDashboard"/>
  </ViewContent>
</template>
