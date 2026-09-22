/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {bottomLegend, cartesianGrid} from '@/util/chartLayout'
import {formatValue, type ResultMatrix} from './groupedResults'

/** A group as a chart draws it: its name, its colour and how many responses stand behind it. */
export interface GroupSeries {
    key: string
    name: string
    color: string
    responseCount: number
}

const ROW_PADDING = 14
const BAR_HEIGHT = 16
const FRAME = 72

/** How tall a grouped chart needs to be for every bar to keep its height. */
export function groupedChartHeight(matrix: ResultMatrix, series: GroupSeries[]): number {
    return matrix.rows.length * (series.length * BAR_HEIGHT + ROW_PADDING) + FRAME
}

/**
 * One question's answers, compared across groups: a horizontal bar per group for every option or
 * statement, in the group's colour, with its value written at the end of the bar.
 *
 * <p>Values are labelled on every bar because several of the group colours are too pale against the
 * light background to be told apart by colour alone; the legend names the groups, and the tooltip
 * adds how many answers stand behind each value.
 */
export function groupedBarOption(matrix: ResultMatrix, series: GroupSeries[], textColor: string, locale: string) {
    return {
        grid: {...cartesianGrid({legend: true}), containLabel: true},
        legend: bottomLegend(textColor, series.map(s => s.name)),
        tooltip: {
            trigger: 'axis',
            axisPointer: {type: 'shadow'},
            formatter: (params: {dataIndex: number, seriesIndex: number, seriesName: string, marker: string}[]) => {
                const row = params[0]?.dataIndex ?? 0
                const lines = params.map(p => {
                    const value = formatValue(matrix.values[row]?.[p.seriesIndex] ?? null, matrix.unit, locale)
                    const count = matrix.counts[row]?.[p.seriesIndex] ?? 0
                    const of = matrix.answers[p.seriesIndex] ?? 0
                    const behind = matrix.unit === 'percent' ? ` (${count}/${of})` : ` (n=${of})`
                    return `${p.marker}${p.seriesName}: ${value}${behind}`
                })
                return [matrix.rows[row] ?? '', ...lines].join('<br/>')
            },
        },
        xAxis: {
            type: 'value',
            max: matrix.unit === 'percent' ? 100 : undefined,
            axisLabel: {color: textColor, formatter: matrix.unit === 'percent' ? '{value} %' : '{value}'},
            splitLine: {lineStyle: {opacity: 0.25}},
        },
        yAxis: {
            type: 'category',
            inverse: true,
            data: matrix.rows,
            axisLabel: {color: textColor, width: 160, overflow: 'truncate'},
        },
        series: series.map((s, g) => ({
            type: 'bar',
            name: s.name,
            barGap: '12%',
            data: matrix.values.map(row => row[g] ?? null),
            itemStyle: {color: s.color, borderRadius: [0, 4, 4, 0]},
            label: {
                show: true,
                position: 'right',
                color: textColor,
                fontSize: 11,
                formatter: (p: {value: number | null}) => formatValue(p.value, matrix.unit, locale),
            },
        })),
    }
}
