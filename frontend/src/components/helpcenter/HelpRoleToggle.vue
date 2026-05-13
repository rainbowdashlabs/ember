/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useSession} from '@/composables/useSession'
import {Roles} from '@/api/types'
import InfoContainer from '@/components/container/InfoContainer.vue'

export interface HelpRole {
  key: string
  label: string
}

const props = defineProps<{
  roles: HelpRole[]
}>()

const activeRole = defineModel<string>({default: ''})

const {t} = useI18n()
const {sessionInfo, loaded, load} = useSession()

function pickRole() {
  if (activeRole.value) return
  if (loaded.value && sessionInfo.value?.roles) {
    const userRoles = sessionInfo.value.roles
    if (userRoles.includes(Roles.MANAGER) && props.roles.some(r => r.key === 'manager')) {
      activeRole.value = 'manager'
    } else if (userRoles.includes(Roles.TEAM) && props.roles.some(r => r.key === 'team')) {
      activeRole.value = 'team'
    } else if (userRoles.includes(Roles.MEMBER_MANAGER) && props.roles.some(r => r.key === 'member-manager')) {
      activeRole.value = 'member-manager'
    } else if (props.roles.length > 0) {
      activeRole.value = props.roles[0].key
    }
  } else if (loaded.value && props.roles.length > 0) {
    activeRole.value = props.roles[0].key
  }
}

onMounted(() => {
  if (!loaded.value) {
    load()
  }
  pickRole()
})

watch(loaded, pickRole)

const hint = computed(() => {
  if (loaded.value && sessionInfo.value?.roles) return ''
  return t('helpCenter.notLoggedIn')
})
</script>

<template>
  <InfoContainer v-if="hint" class="text-sm">
    {{ hint }}
  </InfoContainer>
  <div class="flex flex-wrap gap-2">
    <button
        v-for="role in roles"
        :key="role.key"
        :class="activeRole === role.key
          ? 'bg-primary text-white'
          : 'bg-[var(--bg-accent)] text-[var(--text)] hover:bg-primary/20'"
        class="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
        @click="activeRole = role.key"
    >
      {{ role.label }}
    </button>
  </div>
</template>
