/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {parsePatch} from 'diff'

export interface DiffLine {
  type: 'add' | 'remove' | 'context'
  content: string
  oldLine?: number
  newLine?: number
}

const props = withDefaults(defineProps<{
  patch: string
  showLineNumbers?: boolean
  compact?: boolean
}>(), {
  showLineNumbers: false,
  compact: false,
})

const diffLines = computed<DiffLine[]>(() => {
  const lines: DiffLine[] = []
  const parsed = parsePatch(props.patch)
  if (parsed.length === 0) return lines

  for (const hunk of parsed[0].hunks) {
    let oldLine = hunk.oldStart
    let newLine = hunk.newStart
    for (const line of hunk.lines) {
      if (line.startsWith('+')) {
        lines.push({type: 'add', content: line.substring(1), newLine: newLine++})
      } else if (line.startsWith('-')) {
        lines.push({type: 'remove', content: line.substring(1), oldLine: oldLine++})
      } else {
        lines.push({type: 'context', content: line.substring(1), oldLine: oldLine++, newLine: newLine++})
      }
    }
  }
  return lines
})
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full font-mono border-collapse" :class="compact ? 'text-xs' : 'text-sm'">
      <tbody>
        <tr
            v-for="(line, idx) in diffLines"
            :key="idx"
            :class="{
              'diff-add': line.type === 'add',
              'diff-remove': line.type === 'remove',
            }"
        >
          <template v-if="showLineNumbers">
            <td class="select-none px-2 py-0.5 text-right text-[var(--text-muted)] border-r border-[var(--border)] w-10 text-xs">
              {{ line.oldLine ?? '' }}
            </td>
            <td class="select-none px-2 py-0.5 text-right text-[var(--text-muted)] border-r border-[var(--border)] w-10 text-xs">
              {{ line.newLine ?? '' }}
            </td>
          </template>
          <td class="select-none px-2 py-0.5 w-4 text-center font-bold diff-marker">
            {{ line.type === 'add' ? '+' : line.type === 'remove' ? '-' : '' }}
          </td>
          <td class="px-3 py-0.5 whitespace-pre-wrap break-all diff-content">{{ line.content }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
tr.diff-add {
  background: color-mix(in srgb, #00C507 15%, transparent);
}
tr.diff-add .diff-marker {
  color: #00C507;
}
tr.diff-add .diff-content {
  color: #00C507;
}
tr.diff-remove {
  background: color-mix(in srgb, #ec2929 15%, transparent);
}
tr.diff-remove .diff-marker {
  color: #ec2929;
}
tr.diff-remove .diff-content {
  color: #ec2929;
  text-decoration: line-through;
}
</style>
