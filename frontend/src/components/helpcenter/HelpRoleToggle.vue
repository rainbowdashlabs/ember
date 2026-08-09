/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useSession} from '@/composables/useSession'
import type {StationPermissionName} from '@/api/types'
import InfoContainer from '@/components/container/InfoContainer.vue'

export interface HelpPerspective {
  key: string
  label: string
  permissions: StationPermissionName[]
}

const props = defineProps<{
  perspectives: HelpPerspective[]
}>()

const activeView = defineModel<string>({default: ''})

const {t} = useI18n()
const {sessionInfo, loaded, load} = useSession()

function pickPerspective() {
  if (activeView.value) return
  if (loaded.value && sessionInfo.value?.permissions) {
    const perms = sessionInfo.value.permissions
    const match = props.perspectives.find(p =>
        p.permissions.some(perm => perms.includes(perm))
    )
    activeView.value = match?.key ?? props.perspectives[0]?.key ?? ''
  } else if (loaded.value && props.perspectives.length > 0) {
    activeView.value = props.perspectives[0]?.key ?? ''
  }
}

onMounted(() => {
  if (!loaded.value) {
    load()
  }
  pickPerspective()
})

watch(loaded, pickPerspective)

const isAuthenticated = computed(() => loaded.value && !!sessionInfo.value?.permissions)

const hint = computed(() => {
  if (isAuthenticated.value) return ''
  return t('helpCenter.notLoggedIn')
})
</script>

<template>
  <InfoContainer v-if="hint" class="text-sm">
    {{ hint }}
  </InfoContainer>
  <div v-if="!isAuthenticated" class="flex flex-wrap gap-2">
    <button
        v-for="p in perspectives"
        :key="p.key"
        :class="activeView === p.key
          ? 'bg-primary text-primary-text'
          : 'bg-(--bg-accent) text-(--text) hover:bg-primary/20'"
        class="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
        @click="activeView = p.key"
    >
      {{ p.label }}
    </button>
  </div>
</template>
