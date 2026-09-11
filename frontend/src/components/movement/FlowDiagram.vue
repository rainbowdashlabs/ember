/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import {ItemCustody, ItemOwner, type ItemCustodyName, type ItemOwnerName} from '@/api/inventory'
import {StepSubject, type StepActorName, type StepSubjectName} from '@/api/movements'

/**
 * A chain drawn as the piece's journey rather than listed as its steps.
 *
 * <p>A chain is a piece moving between places, and an exchange is two pieces moving between them at
 * once in opposite directions. A list renders those as equal lines and leaves the reader to assemble
 * the shape; here the shape is the drawing. A track that stops in the post is visibly stranded, which
 * is the same thing the chain validator says in words.
 *
 * <p>It is a picture of the step list beside it and never a replacement for it. The list carries the
 * labels, the ordering controls and everything a screen reader is given, so this is marked decorative.
 */

/**
 * A step as the drawing needs it, which both a configured chain and a walked one satisfy.
 *
 * @param current whether a movement is standing here, absent while a chain is only being configured
 * @param walked  whether a movement has already come past, absent for the same reason
 */
interface DiagramStep {
    id: number
    position: number
    label: string
    actor: StepActorName
    subject: StepSubjectName
    custodyAfter: ItemCustodyName
    picksItem: boolean
    archived: boolean
    current?: boolean
    walked?: boolean
}

const props = defineProps<{
    steps: DiagramStep[]
    /**
     * Who owns the gear this chain is about, where the caller knows it. Custody with the owner is the
     * body above the station for its gear and the station's own shelf for the station's own, so that
     * column is named after whichever of the two it means.
     */
    ownerKind?: ItemOwnerName
}>()

const {t} = useI18n()

/** Unique per instance, because a page shows one of these per chain and a marker id must not collide. */
const arrowId = `flow-arrow-${useId()}`

const COLUMN_WIDTH = 128
const NODE_RADIUS = 13
const SUB_ROW_HEIGHT = 36
const HEADER_HEIGHT = 26
const TRACK_GAP = 26

/**
 * The places a piece can be, left to right as it travels: the body above the station, the post, the
 * station, a partner station, the member. Being lost is no place at all and sits at the end, where a
 * track that reaches it visibly stops.
 */
const CUSTODY_ORDER: ItemCustodyName[] = [
    ItemCustody.WITH_OWNER,
    ItemCustody.IN_TRANSIT,
    ItemCustody.AT_STATION,
    ItemCustody.WITH_PARTNER,
    ItemCustody.WITH_MEMBER,
    ItemCustody.LOST,
]

const walkable = computed(() =>
    props.steps.filter(step => !step.archived).slice().sort((left, right) => left.position - right.position)
)

/** Only the places this chain actually visits, so an ordinary chain stays four columns wide. */
const columns = computed(() => {
    const visited = new Set(walkable.value.map(step => step.custodyAfter))
    return CUSTODY_ORDER.filter(custody => visited.has(custody))
})

interface Node {
    step: DiagramStep
    index: number
    x: number
    y: number
}

interface Edge {
    key: string
    d: string
}

interface Track {
    subject: StepSubjectName
    nodes: Node[]
    edges: Edge[]
    top: number
    height: number
}

/**
 * Where every step sits.
 *
 * <p>The column is the place the step leaves the piece in, so the arrows between them are the journey
 * itself and their direction falls out of it rather than being imposed. Steps of one track that share
 * a column are stacked instead of overlaid: a step that leaves the piece where it already was is
 * somebody confirming rather than something moving, and stacking is what says so.
 */
function pathFor(from: Node, to: Node): string {
    if (from.x === to.x) {
        return `M ${from.x} ${from.y + NODE_RADIUS + 3} L ${to.x} ${to.y - NODE_RADIUS - 3}`
    }
    const gap = to.x > from.x ? NODE_RADIUS + 3 : -(NODE_RADIUS + 3)
    return `M ${from.x + gap} ${from.y} L ${to.x - gap} ${to.y}`
}

const tracks = computed<Track[]>(() => {
    const built: Track[] = []
    let next = HEADER_HEIGHT
    for (const subject of [StepSubject.OUTGOING, StepSubject.INCOMING]) {
        const mine = walkable.value.filter(step => step.subject === subject)
        if (mine.length === 0) continue

        const takenPerColumn = new Map<ItemCustodyName, number>()
        const laid = mine.map(step => {
            const column = columns.value.indexOf(step.custodyAfter)
            const subRow = takenPerColumn.get(step.custodyAfter) ?? 0
            takenPerColumn.set(step.custodyAfter, subRow + 1)
            return {
                step,
                index: walkable.value.indexOf(step) + 1,
                x: column * COLUMN_WIDTH + COLUMN_WIDTH / 2,
                y: subRow * SUB_ROW_HEIGHT + NODE_RADIUS + 2,
            }
        })
        const height = Math.max(...laid.map(node => node.y)) + NODE_RADIUS + 2
        const top = next
        next += height + TRACK_GAP

        const nodes = laid.map(node => ({...node, y: node.y + top}))
        const edges: Edge[] = []
        for (let index = 0; index + 1 < nodes.length; index++) {
            const from = nodes[index]
            const to = nodes[index + 1]
            if (!from || !to) continue
            edges.push({key: `edge-${from.step.id}`, d: pathFor(from, to)})
        }
        built.push({subject, nodes, edges, top, height})
    }
    return built
})

const width = computed(() => Math.max(columns.value.length * COLUMN_WIDTH, COLUMN_WIDTH))

const height = computed(() => {
    const last = tracks.value.at(-1)
    return last ? last.top + last.height : 0
})

function columnLabel(custody: ItemCustodyName): string {
    if (custody === ItemCustody.WITH_OWNER && props.ownerKind === ItemOwner.STATION) {
        return t('inventory.edit.ownerStation')
    }
    return t(`itemDetail.custodyValues.${custody}`)
}
</script>

<template>
  <div v-if="tracks.length > 0" class="overflow-x-auto">
    <svg
        :height="height"
        :viewBox="`0 0 ${width} ${height}`"
        :width="width"
        aria-hidden="true"
        class="text-(--text-muted)"
        role="presentation"
    >
      <defs>
        <marker
            :id="arrowId"
            markerHeight="6"
            markerWidth="7"
            orient="auto-start-reverse"
            refX="6"
            refY="3"
        >
          <path d="M 0 0 L 6 3 L 0 6 z" fill="currentColor"/>
        </marker>
      </defs>

      <text
          v-for="(custody, index) in columns"
          :key="custody"
          :x="index * COLUMN_WIDTH + COLUMN_WIDTH / 2"
          class="fill-current text-[11px]"
          text-anchor="middle"
          y="14"
      >
        {{ columnLabel(custody) }}
      </text>

      <g v-for="track in tracks" :key="track.subject">
        <path
            v-for="edge in track.edges"
            :key="edge.key"
            :d="edge.d"
            :marker-end="`url(#${arrowId})`"
            :stroke-dasharray="track.subject === StepSubject.OUTGOING ? '5 3' : undefined"
            class="stroke-current"
            fill="none"
            stroke-width="1.5"
        />

        <g v-for="node in track.nodes" :key="node.step.id">
          <circle
              :class="node.step.current
                  ? 'fill-(--bg) stroke-primary'
                  : node.step.walked
                      ? 'fill-(--bg) stroke-success'
                      : 'fill-(--bg) stroke-current'"
              :cx="node.x"
              :cy="node.y"
              :r="NODE_RADIUS"
              :stroke-width="node.step.current ? 2.5 : 1.5"
          />
          <text
              :class="node.step.current ? 'fill-primary font-semibold' : 'fill-current'"
              :x="node.x"
              :y="node.y + 4"
              class="text-[11px]"
              text-anchor="middle"
          >
            {{ node.index }}
          </text>
          <g v-if="node.step.picksItem">
            <circle
                :cx="node.x + NODE_RADIUS - 1"
                :cy="node.y - NODE_RADIUS + 1"
                class="fill-(--color-primary) stroke-(--bg)"
                r="6"
                stroke-width="1.5"
            />
            <text
                :x="node.x + NODE_RADIUS - 1"
                :y="node.y - NODE_RADIUS + 4.5"
                class="fill-(--color-primary-text) text-[9px] font-bold"
                text-anchor="middle"
            >
              !
            </text>
          </g>
        </g>
      </g>
    </svg>
  </div>
</template>
