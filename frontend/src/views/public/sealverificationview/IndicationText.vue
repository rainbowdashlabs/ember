/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {ValidationIndication, ValidationSubIndication} from '@/api/generated/schema'

/**
 * A verdict under its standard ETSI names, with the reason after a slash where there is one. Kept in
 * those names on purpose: they are what another validator prints, so a reader can compare the two.
 */
defineProps<{
  indication: ValidationIndication
  subIndication: ValidationSubIndication | null
}>()
</script>

<template>
  <span class="font-mono text-xs break-all">{{ subIndication ? `${indication} / ${subIndication}` : indication }}</span>
</template>
