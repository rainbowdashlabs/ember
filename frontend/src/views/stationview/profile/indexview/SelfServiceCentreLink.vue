/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AppLink from '@/components/navigation/AppLink.vue'
import {StationModule} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * The way from the profile to the documents a member creates for themselves and their children, which
 * live in the documents menu. The profile keeps showing the documents; creating them happens there.
 */
const {t} = useI18n()
const {isModuleEnabled} = useSession()
</script>

<template>
  <AppLink v-if="isModuleEnabled(StationModule.DOCUMENTS)" href="/station/documents"
           :icon="['fas', 'file-circle-plus']" data-testid="profile-own-documents-link">
    {{ t('profile.createDocuments') }}
  </AppLink>
</template>
