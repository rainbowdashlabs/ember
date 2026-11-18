/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    MemberRuleResponse,
    RuleNode,
    RuleField,
    RuleOperatorOption,
    RuleOwners,
    RulePreview,
} from './generated/schema'

/** Whether a rule fills a group or a tag; the two live under their own paths. */
export type RuleOwnerKind = 'group' | 'tag'

function base(kind: RuleOwnerKind, id: number): string {
    return kind === 'group' ? `/groups/${id}/rule` : `/tags/${id}/rule`
}

/** The groups and tags of the station that take their members from a rule. */
export async function listOwners(): Promise<RuleOwners> {
    const res = await client.get<RuleOwners>('/member-rules')
    return res.data
}

/** The fields a check can read, the station's own and its association's. */
export async function listFields(): Promise<RuleField[]> {
    const res = await client.get<RuleField[]>('/member-rules/fields')
    return res.data
}

/** Every operator a check can use, per subject and field type. */
export async function listOperators(): Promise<RuleOperatorOption[]> {
    const res = await client.get<RuleOperatorOption[]>('/member-rules/operators')
    return res.data
}

/** The rule of a group or tag, or null where it is kept by hand. */
export async function getRule(kind: RuleOwnerKind, id: number): Promise<RuleNode | null> {
    const res = await client.get<MemberRuleResponse>(base(kind, id))
    return res.data.rule
}

/** Saves the rule of a group or tag; its members follow the rule from then on. */
export async function saveRule(kind: RuleOwnerKind, id: number, rule: RuleNode): Promise<RuleNode | null> {
    const res = await client.put<MemberRuleResponse>(base(kind, id), {rule})
    return res.data.rule
}

/** Removes the rule; the group or tag keeps its members and is kept by hand. */
export async function removeRule(kind: RuleOwnerKind, id: number): Promise<void> {
    await client.delete(base(kind, id))
}

/** Who the rule would hold today, without saving it. */
export async function previewRule(kind: RuleOwnerKind, id: number, rule: RuleNode): Promise<RulePreview> {
    const res = await client.post<RulePreview>(`${base(kind, id)}/preview`, {rule})
    return res.data
}
