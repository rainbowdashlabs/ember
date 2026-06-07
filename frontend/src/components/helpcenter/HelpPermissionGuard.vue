/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useSession} from '@/composables/useSession'
import type {StationPermissionName} from '@/api/types'

const props = defineProps<{
  permissions: StationPermissionName[]
  label: string
}>()

const {sessionInfo, loaded, load} = useSession()
const expanded = ref(false)

onMounted(() => {
  if (!loaded.value) load()
})

const isAuthenticated = computed(() => loaded.value && !!sessionInfo.value?.permissions)

const hasPermission = computed(() => {
  if (!isAuthenticated.value) return false
  const perms = sessionInfo.value!.permissions!
  return props.permissions.some(p => perms.includes(p))
})
</script>

<template>
  <!-- Authenticated: show only if user has the required permission -->
  <template v-if="isAuthenticated">
    <slot v-if="hasPermission"/>
  </template>

  <!-- Unauthenticated: collapsible toggle so the reader can explore all content -->
  <template v-else>
    <div class="rounded-lg border border-[var(--border)] overflow-hidden">
      <button
          class="w-full flex items-center justify-between gap-2 px-4 py-2.5 text-sm font-medium
                 bg-[var(--bg-accent)] text-[var(--text)] hover:bg-primary/10 transition-colors"
          @click="expanded = !expanded"
      >
        <span class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'shield-halved']" class="text-primary w-4 h-4"/>
          {{ label }}
        </span>
        <font-awesome-icon
            :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
            class="w-3 h-3 text-[var(--text-muted)]"
        />
      </button>
      <div v-if="expanded" class="px-4 py-3 space-y-4">
        <slot/>
      </div>
    </div>
  </template>
</template>
