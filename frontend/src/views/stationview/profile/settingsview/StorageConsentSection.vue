/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import StorageScopeChoice from '@/components/consent/StorageScopeChoice.vue'
import {getGrantedScopes, setGrantedScopes, type StorageNecessityName} from '@/api/storage'

/**
 * The standing decision about what may stay in the browser. Withdrawing a group takes effect at
 * once: its values are removed as the switch flips, so nothing lingers until the next visit.
 */
const {t} = useI18n()

const scopes = ref<StorageNecessityName[]>(getGrantedScopes())

watch(scopes, next => setGrantedScopes(next))
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('storageConsent.settingsTitle') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('storageConsent.settingsHint') }}</p>

    <StorageScopeChoice v-model="scopes"/>

    <LinkButton @click="$router.push('/privacy')">{{ t('storageConsent.listLink') }}</LinkButton>
  </NeutralContainer>
</template>
