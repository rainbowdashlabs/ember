/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import NavEntry from '@/components/navigation/NavEntry.vue'
import NavSectionRow from '@/components/navigation/NavSectionRow.vue'
import NavSectionView from '@/components/navigation/NavSectionView.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'
import {useSidebarMode} from '@/composables/useSidebarMode'
import type {NavGroup, NavNode} from '@/data/sidebar/navTypes'
import {entryHolding} from '@/util/navTree'

/**
 * A sidebar declared as data, shown one section at a time.
 *
 * <p>The full list holds pages and sections. Opening a section shows only that section in place of the
 * list and opens its first page, so only one section is ever open and nothing piles up. Arriving on a
 * page by any way at all (link, bookmark, reload, palette) shows the section that page is in; going back
 * returns to the full list with focus on the section just left.
 *
 * <p>The collapsed rail keeps its icons with hover flyouts, since there is no room there to open a
 * section in place.
 *
 * <p>An account that chose the tree ({@link useSidebarMode}) sees every section at once instead, each
 * opened in place and the one holding the page shown opened by itself, the way the sidebar used to work.
 *
 * <p>Only the view coming in slides; the one going out leaves at once. A leave that waits for its own
 * animation keeps the old view, and its links, on the page whenever the browser skips the end of it.
 */
const props = defineProps<{
  nodes: NavNode[]
}>()

const emit = defineEmits<{
  (e: 'navigate'): void
}>()

const route = useRoute()
const {collapsed} = useSidebarCollapse()
const {isDesktop} = useBreakpoint()
const {tree} = useSidebarMode()

const rail = computed(() => collapsed.value && isDesktop.value)

const root = ref<HTMLElement | null>(null)
const openKey = ref<string | null>(null)
/** The page on which the reader went back to all sections, which keeps them there until they move on. */
const leftAt = ref<string | null>(null)

/** The top-level entry holding the page shown, which is lit in the full list. */
const holding = computed(() => entryHolding(props.nodes, route.path))

const openSection = computed(() => props.nodes.find((node): node is NavGroup => node.kind === 'group' && node.key === openKey.value))

watch([() => route.path, () => holding.value?.key, () => holding.value?.kind], () => {
  if (!holding.value || route.path === leftAt.value) return
  leftAt.value = null
  openKey.value = holding.value.kind === 'group' ? holding.value.key : null
}, {immediate: true})

function open(section: NavGroup) {
  leftAt.value = null
  openKey.value = section.key
}

async function back() {
  const left = openKey.value
  leftAt.value = route.path
  openKey.value = null
  await nextTick()
  root.value?.querySelector<HTMLElement>(`[data-section="${left}"]`)?.focus()
}
</script>

<template>
  <div ref="root" class="flex flex-col gap-1">
    <template v-if="rail || tree">
      <NavEntry v-for="node in nodes" :key="node.key" :node="node" @navigate="emit('navigate')"/>
    </template>
    <Transition v-else
                enter-active-class="motion-safe:transition motion-safe:duration-150 motion-safe:ease-out"
                :enter-from-class="openSection ? 'translate-x-4 opacity-0' : '-translate-x-4 opacity-0'">
      <NavSectionView v-if="openSection" :key="openSection.key" :section="openSection" @back="back" @navigate="emit('navigate')"/>
      <div v-else class="flex flex-col gap-1">
        <template v-for="node in nodes" :key="node.key">
          <NavSectionRow v-if="node.kind === 'group'" :section="node" :active="holding?.key === node.key" @open="open(node)"/>
          <NavEntry v-else :node="node" :data-active="holding?.key === node.key ? 'true' : undefined" @navigate="emit('navigate')"/>
        </template>
      </div>
    </Transition>
  </div>
</template>
