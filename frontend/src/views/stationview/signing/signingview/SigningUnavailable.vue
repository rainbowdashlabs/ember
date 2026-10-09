/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import type {Failure} from '@/util/failure'

/**
 * A field that cannot be signed from here: already signed, never the reader's, or gone. Says which, and
 * offers the way back to what still waits for the reader, since a link from a mail is often opened
 * after somebody else has already signed.
 */
defineProps<{failure: Failure}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4 max-w-3xl" data-testid="signing-unavailable">
    <FailureAlert :failure="failure"/>
    <AppLink href="/station/requirements" :icon="['fas', 'clipboard-check']">{{ t('signing.done.toRequirements') }}</AppLink>
  </div>
</template>
