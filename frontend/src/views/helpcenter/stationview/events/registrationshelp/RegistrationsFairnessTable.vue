/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {MemberIdentity} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <table class="w-full text-sm">
    <thead>
      <tr class="text-xs text-(--text-muted) border-b border-(--border)">
        <th class="p-2 text-left">{{ t('eventsRegistrations.member') }}</th>
        <th class="p-2 text-center">{{ t('eventsRegistrations.score') }}</th>
        <th class="p-2 text-center">{{ t('eventsRegistrations.acceptedCol') }}</th>
        <th class="p-2 text-center">{{ t('eventsRegistrations.deniedCol') }}</th>
        <th class="p-2 text-center">{{ t('eventsRegistrations.date') }}</th>
        <th class="p-2"></th>
      </tr>
    </thead>
    <tbody>
      <tr class="border-b border-(--border)">
        <td class="p-2">
          <MemberName :identity="{stationUid: '', memberUid: '', name: 'Max Mustermann'} as MemberIdentity"/>
        </td>
        <td class="p-2 text-center font-bold text-error">8.5</td>
        <td class="p-2 text-center"><SuccessBadge>3</SuccessBadge></td>
        <td class="p-2 text-center"><ErrorBadge>2</ErrorBadge></td>
        <td class="p-2 text-center text-xs text-(--text-muted)">2026-05-25</td>
        <td class="p-2">
          <div class="flex items-center gap-1 justify-end">
            <PrimaryButton compact :icon="['fas', 'check']">{{ t('eventsRegistrations.accept') }}</PrimaryButton>
            <ErrorButton compact :icon="['fas', 'xmark']">{{ t('eventsRegistrations.deny') }}</ErrorButton>
          </div>
        </td>
      </tr>
      <tr class="border-b border-(--border)">
        <td class="p-2">
          <MemberName :identity="{stationUid: '', memberUid: '', name: 'Erika Muster'} as MemberIdentity"/>
        </td>
        <td class="p-2 text-center font-bold text-info">4.2</td>
        <td class="p-2 text-center"><SuccessBadge>5</SuccessBadge></td>
        <td class="p-2 text-center">0</td>
        <td class="p-2 text-center text-xs text-(--text-muted)">2026-05-25</td>
        <td class="p-2">
          <div class="flex items-center gap-1 justify-end">
            <PrimaryButton compact :icon="['fas', 'check']">{{ t('eventsRegistrations.accept') }}</PrimaryButton>
            <ErrorButton compact :icon="['fas', 'xmark']">{{ t('eventsRegistrations.deny') }}</ErrorButton>
          </div>
        </td>
      </tr>
    </tbody>
  </table>
</template>
