/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * The one line that has to make it to the other machine, and the code it carries.
 *
 * The answers are shown underneath as well. A command that fetches something you cannot see is a
 * command worth being suspicious of, and anybody about to pipe this into a shell deserves to read
 * what it will do first.
 */
const props = defineProps<{
  code: string
  validForHours: number
  options: Record<string, string>
}>()

const copied = ref(false)

const origin = computed(() => (import.meta.client ? window.location.origin : 'https://ember-panel.de'))

const command = computed(() => `curl -fsSL ${origin.value}/install.sh | bash -s ${props.code}`)

const shown = computed(() => Object.entries(props.options))

async function copy() {
  await navigator.clipboard.writeText(command.value)
  copied.value = true
  setTimeout(() => (copied.value = false), 2000)
}
</script>

<template>
  <SuccessContainer class="space-y-3">
    <SectionHeader>Auf dem Server ausführen</SectionHeader>

    <div class="flex items-center gap-3 flex-wrap">
      <span class="text-sm text-(--text-muted)">Code</span>
      <span class="rounded bg-(--bg-accent) px-3 py-1 text-2xl font-mono font-bold tracking-widest">{{ code }}</span>
      <MutedText size="sm" tag="span">gültig für {{ validForHours }} Stunden</MutedText>
    </div>

    <pre class="overflow-x-auto rounded bg-(--bg-accent) p-3 text-sm font-mono">{{ command }}</pre>

    <SecondaryButton :icon="['fas', copied ? 'check' : 'copy']" @click="copy">
      {{ copied ? 'Kopiert' : 'Befehl kopieren' }}
    </SecondaryButton>

    <details class="text-sm">
      <summary class="cursor-pointer text-(--text-muted)">Was hinter dem Code steht</summary>
      <dl class="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 font-mono text-xs">
        <template v-for="[key, value] in shown" :key="key">
          <dt class="text-(--text-muted)">{{ key }}</dt>
          <dd>{{ value }}</dd>
        </template>
      </dl>
    </details>
  </SuccessContainer>
</template>
