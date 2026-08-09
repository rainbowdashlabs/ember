/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import IdentityButton from '@/components/button/IdentityButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'

/** Fake save that resolves after a short delay so the showcase button plays its pending state. */
function demoSave(): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, 800))
}
</script>

<template>
  <div class="flex flex-wrap gap-2 items-center"><PrimaryButton>Primary</PrimaryButton><SecondaryButton>Secondary</SecondaryButton><SuccessButton>Success</SuccessButton><ErrorButton>Error</ErrorButton><InfoButton>Info</InfoButton></div>
  <div class="flex flex-wrap gap-2 items-center"><PrimaryButton :icon="['fas', 'plus']">With Icon</PrimaryButton><SecondaryButton :icon="['fas', 'download']">Download</SecondaryButton><SuccessButton :icon="['fas', 'check']">Confirm</SuccessButton><ErrorButton :icon="['fas', 'trash']">Delete</ErrorButton></div>
  <div class="flex flex-wrap gap-2 items-center"><PrimaryButton compact>Compact</PrimaryButton><SecondaryButton compact>Compact</SecondaryButton><SuccessButton compact>Compact</SuccessButton></div>
  <div class="flex flex-col gap-2 max-w-xs"><PrimaryButton full-width :icon="['fas', 'plus']">Full width</PrimaryButton><SecondaryButton full-width>Full width</SecondaryButton><InfoButton compact full-width>Full width compact</InfoButton></div>
  <div class="flex flex-wrap gap-2 items-center"><SaveButton :action="demoSave"/><SaveButton :action="demoSave">Custom text</SaveButton></div>
  <div class="flex flex-wrap gap-2 items-center"><PrimaryButton disabled>Disabled</PrimaryButton><SecondaryButton disabled>Disabled</SecondaryButton><SuccessButton disabled>Disabled</SuccessButton><ErrorButton disabled>Disabled</ErrorButton><InfoButton disabled>Disabled</InfoButton></div>
  <div class="flex flex-wrap gap-2 items-center">
    <IdentityButton><font-awesome-icon :icon="['fas', 'user']" class="h-4 w-4"/>Identity</IdentityButton>
    <LinkButton>Link Button</LinkButton>
    <LinkButton disabled>Link (disabled)</LinkButton>
  </div>
</template>
