/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import SectionHeader from '@/components/typography/SectionHeader.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'

const lastScanned = ref('')
const lastContinuous = ref('')
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Barcode Scanner</SectionHeader>
    <div class="flex items-center gap-3">
      <ScanButton @decoded="lastScanned = $event"/>
      <span class="text-sm text-(--text-muted)">{{ lastScanned ? `Decoded: ${lastScanned}` : 'No scan yet' }}</span>
    </div>
    <div class="flex items-center gap-3">
      <ScanButton mode="continuous" @decoded="lastContinuous = $event"/>
      <span class="text-sm text-(--text-muted)">{{ lastContinuous ? `Continuous decoded: ${lastContinuous}` : 'Continuous mode' }}</span>
    </div>
  </section>
</template>
