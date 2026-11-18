/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RuleField;
import dev.chojo.ember.feature.members.entity.RuleMember;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleNodeKind;
import dev.chojo.ember.feature.members.entity.RuleOperatorOption;
import dev.chojo.ember.feature.members.entity.RuleOperators;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleOwners;
import dev.chojo.ember.feature.members.entity.RulePreview;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.MemberRuleRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reading, saving, trying out and removing the rule of a group or tag.
 *
 * <p>A rule is saved only when every check names a subject, an operator its subject offers for the field it
 * reads, and the value that operator needs, and every field, group or tag it names belongs to the station. A
 * block holds at least one node, since an empty one would match everybody or nobody without asking anything. A
 * rule may not ask about itself through other rules, may not be larger than {@link #MAX_DEPTH} levels and
 * {@link #MAX_CHECKS} checks, and may not fill a group that is part of a group set. A rule for a group that grants
 * permissions decides who receives them, so the caller has to hold them all, as for adding members by hand, and it
 * may not read answers the members give themselves (see {@link SelfAnsweredRules}).
 *
 * <p>A rule that reads profile answers, ages or join dates answers questions about member data, so saving or
 * trying one takes the right to read members. A rule may not choose people by a private tag, which only labels
 * them. Saving a rule changes who is in its group or tag, so the caller also needs the permissions of every group
 * whose rule asks about it, directly or through other rules, as for a hand change. A group that registration codes,
 * invites or the waiting list hand out takes no rule, since those would no longer place anybody in it.
 *
 * <p>Saving does not run the rule on the request: the station's rules run on their own lane a few seconds after
 * it has landed, so a save never meets a run in progress.
 */
@Singleton
public class MemberRuleEditService {
    /** How many levels of blocks and checks a rule may have. */
    public static final int MAX_DEPTH = 6;
    /** How many checks a rule may hold. */
    public static final int MAX_CHECKS = 50;

    private final MemberRuleRepository ruleRepository;
    private final MemberGroupRepository groupRepository;
    private final UserTagRepository tagRepository;
    private final ProfileFieldRepository fieldRepository;
    private final Provider<ClusterProfileFieldService> clusterFields;
    private final StationRepository stationRepository;
    private final GroupMembershipService memberships;
    private final MemberRuleService rules;
    private final Provider<MemberNameResolver> names;
    private final SelfAnsweredRules selfAnswered;

    @Inject
    public MemberRuleEditService(
            MemberRuleRepository ruleRepository,
            MemberGroupRepository groupRepository,
            UserTagRepository tagRepository,
            ProfileFieldRepository fieldRepository,
            Provider<ClusterProfileFieldService> clusterFields,
            StationRepository stationRepository,
            GroupMembershipService memberships,
            MemberRuleService rules,
            Provider<MemberNameResolver> names,
            SelfAnsweredRules selfAnswered) {
        this.ruleRepository = ruleRepository;
        this.groupRepository = groupRepository;
        this.tagRepository = tagRepository;
        this.fieldRepository = fieldRepository;
        this.clusterFields = clusterFields;
        this.stationRepository = stationRepository;
        this.memberships = memberships;
        this.rules = rules;
        this.names = names;
        this.selfAnswered = selfAnswered;
    }

    /** Every operator a check can use, per subject and field type, as the builder offers them. */
    public static List<RuleOperatorOption> operators() {
        var options = new ArrayList<RuleOperatorOption>();
        for (var subject : RuleSubject.values()) {
            if (!subject.readsField()) {
                RuleOperators.offered(subject, null)
                        .forEach(
                                (operator, need) -> options.add(new RuleOperatorOption(subject, null, operator, need)));
                continue;
            }
            for (var type : FieldType.values()) {
                RuleOperators.offered(subject, type)
                        .forEach(
                                (operator, need) -> options.add(new RuleOperatorOption(subject, type, operator, need)));
            }
        }
        return List.copyOf(options);
    }

    /**
     * The groups and tags of a station that take their members from a rule, leaving out private tags for a
     * reader who may not see them.
     *
     * @param session the reader
     */
    public RuleOwners owners(StationSession session) {
        var owners = ruleRepository.owners(session.stationId());
        if (session.hasPermission(StationPermission.MEMBER_READ)) return owners;
        var hidden = tagRepository.findPrivateIds(owners.tagIds());
        return new RuleOwners(
                owners.groupIds(),
                owners.tagIds().stream().filter(id -> !hidden.contains(id)).toList());
    }

    /**
     * The fields a check of a rule at a station can read: its own, then its association's, each in their order,
     * leaving out those no check reads and those the reader may not read, since a rule and its preview would
     * otherwise tell them who answered what.
     *
     * @param session the reader, at the station
     */
    public List<RuleField> fields(StationSession session) {
        int stationId = session.stationId();
        var readable = readableFields(session);
        var fields = new ArrayList<RuleField>();
        for (var field : fieldRepository.findByStation(stationId)) {
            if (readable.station().contains(field.id()) && RuleOperators.readable(field.fieldType())) {
                fields.add(
                        RuleField.of(FieldOrigin.STATION, field.id(), field.name(), field.fieldType(), field.config()));
            }
        }
        Integer clusterId = stationRepository
                .findById(stationId)
                .map(station -> station.clusterId())
                .orElse(null);
        if (clusterId != null) {
            for (var field : clusterFields.get().findByCluster(clusterId)) {
                if (readable.cluster().contains(field.id()) && RuleOperators.readable(field.fieldType())) {
                    fields.add(RuleField.of(
                            FieldOrigin.CLUSTER, field.id(), field.name(), field.fieldType(), field.config()));
                }
            }
        }
        return List.copyOf(fields);
    }

    /**
     * The rule of a group or tag, if it takes its members from one.
     *
     * @param owner the group or tag, already checked to belong to the caller's station
     */
    public Optional<RuleNode> find(RuleOwner owner) {
        return ruleRepository.find(owner);
    }

    /**
     * Checks and saves the rule of a group or tag, replacing any rule it had; the station's rules then run.
     *
     * @param session who is asking, at the station of the group or tag
     * @param owner   the group or tag, already checked to belong to the caller's station
     * @param root    the rule
     * @return the rule as saved
     */
    public RuleNode save(StationSession session, RuleOwner owner, RuleNode root) {
        int stationId = session.stationId();
        requireValid(session, root);
        if (owner.kind() == RuleOwner.Kind.GROUP) {
            var group = requireGroup(stationId, owner.id());
            if (group.groupSetId() != null) throw MemberRefusal.RULE_GROUP_IN_A_SET.raise();
            if (memberships.handedOut(group.id())) throw MemberRefusal.RULE_GROUP_HANDED_OUT.raise(group.name());
            memberships.requireMayFill(group, session.user(), MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_RULE);
        }
        memberships.requireMayChangeThroughRules(stationId, owner, session.user());
        var all = new HashMap<>(ruleRepository.findByStation(stationId));
        all.put(owner, root);
        var circle = RuleDependencies.cycle(all);
        if (circle.isPresent()) {
            String chain = circle.get().stream().map(this::nameOf).collect(Collectors.joining(" > "));
            throw MemberRefusal.RULE_IN_A_CIRCLE.raise(RefusalDetail.text(chain));
        }
        selfAnswered.requireNone(owner, all);
        Transactions.run(() -> {
            ruleRepository.replace(stationId, owner, root);
            Transactions.afterCommit(() -> rules.stationChanged(stationId));
        });
        return ruleRepository.find(owner).orElse(root);
    }

    /**
     * Removes the rule of a group or tag, which keeps the members it has and is kept by hand from then on.
     *
     * @param owner the group or tag, already checked to belong to the caller's station
     * @return whether it had a rule
     */
    public boolean remove(RuleOwner owner) {
        return ruleRepository.delete(owner);
    }

    /**
     * Who a rule would take into its group or tag today, without saving it, and how that differs from who is in
     * it now.
     *
     * @param session who is asking, at the station of the group or tag
     * @param owner   the group or tag, already checked to belong to the caller's station
     * @param root    the rule to try
     */
    public RulePreview preview(StationSession session, RuleOwner owner, RuleNode root) {
        int stationId = session.stationId();
        requireValid(session, root);
        List<StationUserType> bound = List.of();
        List<StationMember> current;
        if (owner.kind() == RuleOwner.Kind.GROUP) {
            bound = requireGroup(stationId, owner.id()).userTypes();
            current = groupRepository.findMembers(owner.id());
        } else {
            current = tagRepository.findMembers(owner.id());
        }
        var today = LocalDate.now(
                StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null)));
        var matching = ruleRepository.matching(stationId, root, today, bound);
        var now = new HashSet<Integer>();
        current.forEach(member -> now.add(member.id()));
        var matched = new HashSet<>(matching);
        return new RulePreview(
                named(matching),
                named(matching.stream().filter(id -> !now.contains(id)).toList()),
                named(now.stream().filter(id -> !matched.contains(id)).sorted().toList()));
    }

    private List<RuleMember> named(List<Integer> ids) {
        var resolver = names.get();
        return ids.stream()
                .map(id -> new RuleMember(id, resolver.identified(id)))
                .toList();
    }

    /**
     * The fields of the station and of its association a reader may read, by id. A rule may name no other: to its
     * author a field they cannot read is one that is not there.
     */
    private ReadableFields readableFields(StationSession session) {
        var scopes = ProfileFieldScopes.readableBy(session.user().permissions());
        var station = new HashSet<Integer>();
        fieldRepository.findReadableBy(session.stationId(), scopes).forEach(field -> station.add(field.id()));
        var cluster = new HashSet<Integer>();
        for (var scope : scopes) {
            clusterFields
                    .get()
                    .findForStation(session.stationId(), scope)
                    .forEach(found -> cluster.add(found.field().id()));
        }
        return new ReadableFields(Set.copyOf(station), Set.copyOf(cluster));
    }

    /** The ids of the station's and of its association's fields a reader may read. */
    private record ReadableFields(Set<Integer> station, Set<Integer> cluster) {}

    private void requireValid(StationSession session, RuleNode root) {
        int stationId = session.stationId();
        requireWellFormed(root, true);
        if (!root.kind().isBlock()) throw MemberRefusal.RULE_NEEDS_A_BLOCK.raise();
        boolean readsMemberData = root.checks().stream().anyMatch(check -> readsMemberData(check.subject()));
        if (readsMemberData && !session.hasPermission(StationPermission.MEMBER_READ)) {
            throw MemberRefusal.RULE_READS_MEMBER_DATA.raise();
        }
        int checks = root.checks().size();
        if (root.depth() > MAX_DEPTH) throw MemberRefusal.RULE_TOO_LARGE.raise(RefusalDetail.count(MAX_DEPTH));
        if (checks > MAX_CHECKS) throw MemberRefusal.RULE_TOO_LARGE.raise(RefusalDetail.count(MAX_CHECKS));
        Integer clusterId = stationRepository
                .findById(stationId)
                .map(station -> station.clusterId())
                .orElse(null);
        var readable = readableFields(session);
        root.nodes().forEach(node -> requireValidNode(stationId, clusterId, readable, node));
    }

    /**
     * Refuses a tree as it may arrive from a request body, with a node that names no kind, a block whose list of
     * children is missing or holds an empty place, before anything walks it.
     */
    private static void requireWellFormed(@Nullable RuleNode node, boolean root) {
        if (node == null || node.kind() == null) {
            throw root ? MemberRefusal.RULE_NEEDS_A_BLOCK.raise() : MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
        }
        var children = node.children();
        if (children == null) throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
        for (var child : children) requireWellFormed(child, false);
    }

    private static boolean readsMemberData(@Nullable RuleSubject subject) {
        return subject == RuleSubject.FIELD
                || subject == RuleSubject.CLUSTER_FIELD
                || subject == RuleSubject.AGE
                || subject == RuleSubject.MEMBER_SINCE;
    }

    private void requireValidNode(int stationId, @Nullable Integer clusterId, ReadableFields readable, RuleNode node) {
        if (node.kind() != RuleNodeKind.CHECK) {
            boolean bare = node.subject() == null
                    && node.operator() == null
                    && node.fieldId() == null
                    && node.clusterFieldId() == null
                    && node.groupId() == null
                    && node.tagId() == null
                    && node.value() == null
                    && node.referenceDate() == null;
            if (!bare) throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
            if (node.children().isEmpty()) throw MemberRefusal.RULE_BLOCK_EMPTY.raise();
            return;
        }
        var subject = node.subject();
        var operator = node.operator();
        if (subject == null || operator == null || !node.children().isEmpty()) {
            throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
        }
        FieldType type = targetType(stationId, clusterId, readable, subject, node);
        var need = RuleOperators.need(subject, type, operator).orElseThrow(MemberRefusal.RULE_CHECK_INCOMPLETE::raise);
        if (!node.valueOrEmpty().satisfies(need)) throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
        if (node.referenceDate() != null && !RuleOperators.countsFromReference(subject, operator)) {
            throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
        }
    }

    /** Checks that a check names exactly the target its subject reads, at this station, and says its field type. */
    private @Nullable FieldType targetType(
            int stationId, @Nullable Integer clusterId, ReadableFields readable, RuleSubject subject, RuleNode node) {
        Integer field = node.fieldId();
        Integer clusterField = node.clusterFieldId();
        Integer group = node.groupId();
        Integer tag = node.tagId();
        boolean onlyField = field != null && clusterField == null && group == null && tag == null;
        boolean onlyClusterField = clusterField != null && field == null && group == null && tag == null;
        return switch (subject) {
            case GROUP -> {
                if (group == null || field != null || clusterField != null || tag != null) {
                    throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
                }
                requireGroup(stationId, group);
                yield null;
            }
            case TAG -> {
                if (tag == null || field != null || clusterField != null || group != null) {
                    throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
                }
                var found = tagRepository
                        .findById(tag)
                        .filter(candidate -> candidate.stationId() == stationId)
                        .orElseThrow(MemberRefusal.RULE_TARGET_NOT_HERE::raise);
                if (found.visibility().restricted()) throw MemberRefusal.TAG_PRIVATE_CHOOSES_NOBODY.raise();
                yield null;
            }
            case USER_TYPE, MEMBER_SINCE -> {
                if (field != null || clusterField != null || group != null || tag != null) {
                    throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
                }
                yield null;
            }
            case FIELD -> {
                if (!onlyField) throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
                yield stationFieldType(stationId, readable, field);
            }
            case CLUSTER_FIELD -> {
                if (!onlyClusterField) throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
                yield clusterFieldType(clusterId, readable, clusterField);
            }
            case AGE -> {
                if (onlyField) yield stationFieldType(stationId, readable, field);
                if (onlyClusterField) yield clusterFieldType(clusterId, readable, clusterField);
                throw MemberRefusal.RULE_CHECK_INCOMPLETE.raise();
            }
        };
    }

    private FieldType stationFieldType(int stationId, ReadableFields readable, int fieldId) {
        if (!readable.station().contains(fieldId)) throw MemberRefusal.RULE_TARGET_NOT_HERE.raise();
        return fieldRepository
                .findById(fieldId)
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(MemberRefusal.RULE_TARGET_NOT_HERE::raise)
                .fieldType();
    }

    private FieldType clusterFieldType(@Nullable Integer clusterId, ReadableFields readable, int fieldId) {
        if (clusterId == null || !readable.cluster().contains(fieldId))
            throw MemberRefusal.RULE_TARGET_NOT_HERE.raise();
        return clusterFields.get().findByCluster(clusterId).stream()
                .filter(found -> found.id() == fieldId)
                .findFirst()
                .orElseThrow(MemberRefusal.RULE_TARGET_NOT_HERE::raise)
                .fieldType();
    }

    private MemberGroup requireGroup(int stationId, int groupId) {
        return groupRepository
                .findById(groupId)
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(MemberRefusal.RULE_TARGET_NOT_HERE::raise);
    }

    private String nameOf(RuleOwner owner) {
        if (owner.kind() == RuleOwner.Kind.GROUP) {
            return groupRepository.findById(owner.id()).map(MemberGroup::name).orElse("#" + owner.id());
        }
        return tagRepository.findById(owner.id()).map(UserTag::name).orElse("#" + owner.id());
    }
}
