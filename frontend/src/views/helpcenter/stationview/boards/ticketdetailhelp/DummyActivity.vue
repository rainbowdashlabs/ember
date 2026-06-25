/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex gap-2">
    <div class="flex-1">
      <div class="flex items-center gap-2 text-xs text-(--text-muted)">
        <MemberName :identity="{stationUid: 's1', memberUid: 'u2', displayTag: {name: 'Lisa Schmidt', color: ''}}"
                    size="sm" class="font-medium"/>
        <span>05.06.26, 14:30</span>
      </div>
      <p class="text-sm mt-0.5">{{ t('helpCenter.ticketDetail.commentExample1') }}</p>
      <div class="ml-6 mt-2 border-l-2 border-(--border) pl-3">
        <div class="flex items-center gap-2 text-xs text-(--text-muted)">
          <MemberName :identity="{stationUid: 's1', memberUid: 'u1', displayTag: {name: 'Max Mustermann', color: ''}}"
                      size="sm" class="font-medium"/>
          <span>05.06.26, 15:10</span>
        </div>
        <p class="text-sm mt-0.5">{{ t('helpCenter.ticketDetail.commentReply1') }}</p>
      </div>
    </div>
  </div>
</template>
