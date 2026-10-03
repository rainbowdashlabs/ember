/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AppLink from '@/components/navigation/AppLink.vue'
import {StationPermission} from '@/api/generated/schema'
import {usePermissions} from '@/composables/usePermissions'

/**
 * The way from the store to the rules that file mail attachments into it. The rules configure the
 * station's mailboxes, so they stay with the mail settings; this only points there for whoever may
 * change them.
 */
const {t} = useI18n()
const {hasPermission} = usePermissions()
</script>

<template>
  <AppLink v-if="hasPermission(StationPermission.STATION_MAIL)" href="/station/manage/mail-import"
           :icon="['fas', 'envelope-open-text']" data-testid="documents-filing-rules">
    {{ t('documents.filingRules') }}
  </AppLink>
</template>
