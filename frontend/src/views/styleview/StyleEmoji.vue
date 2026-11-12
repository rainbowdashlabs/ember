/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmojiPicker from '@/components/emoji/EmojiPicker.vue'

const picked = ref('')
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Emoji Picker</SectionHeader>
    <div class="flex items-start gap-3">
      <div class="rounded-theme border border-(--border)">
        <EmojiPicker @pick="picked = $event"/>
      </div>
      <span class="text-2xl">{{ picked }}</span>
    </div>
  </section>
</template>
