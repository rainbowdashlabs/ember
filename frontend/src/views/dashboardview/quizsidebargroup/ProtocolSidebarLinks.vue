/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const emit = defineEmits<{
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {hasPermission, canTestProtocol} = useSession()
</script>

<template>
  <SidebarLink v-if="hasPermission(StationPermission.PROTOCOL_CREATE)" :icon="['fas', 'clipboard-list']" name="protocol-list" to="/station/protocols" @navigate="emit('navigate')">
    {{ t('sidebar.protocols') }}
  </SidebarLink>
  <SidebarLink v-if="canTestProtocol()" :icon="['fas', 'clipboard-check']" name="protocol-run-list" to="/station/protocols/runs" @navigate="emit('navigate')">
    {{ t('sidebar.protocolRuns') }}
  </SidebarLink>
</template>
