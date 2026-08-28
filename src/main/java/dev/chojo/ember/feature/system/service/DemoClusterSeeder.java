/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.service.ClusterApplicationService;
import dev.chojo.ember.feature.cluster.service.ClusterContentService;
import dev.chojo.ember.feature.cluster.service.ClusterInventoryService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberService;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.cluster.service.ClusterStationGroupService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageQuotaService;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.InventoryFieldDefinitionService;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Seeds the body above the demo station: a district association with its own station, the people who act for
 * it, the gear it owns and the things it has said.
 *
 * <p>Per the seeder convention it goes through the services wherever one exists, so the domain events fire on
 * their own and the demo instance looks like one somebody has been using rather than one somebody inserted.
 *
 * <p>Three shapes of station are deliberately present, because they behave differently and a demo showing
 * only one of them teaches the wrong thing: a station the cluster made itself, a station that joined, and a
 * standing station whose request is still waiting. Gear is spread the same way, across every custody it can
 * be in, with one piece whose owner is not on this instance at all.
 */
@Singleton
public class DemoClusterSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoClusterSeeder.class);

    /** Room is written in the units the screens show it in, so the seeded figures read as round numbers. */
    private static final long MIB = 1024L * 1024L;

    private static final long GIB = 1024L * MIB;

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final ClusterService clusterService;
    private final ClusterMemberService memberService;
    private final ClusterInventoryService clusterInventoryService;
    private final ClusterProfileFieldService fieldService;
    private final ClusterStationGroupService stationGroupService;
    private final ClusterContentService contentService;
    private final ClusterApplicationService applicationService;
    private final ClusterStorageQuotaService quotaService;
    private final StationRepository stationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryFieldDefinitionService fieldDefinitionService;
    private final ItemCustodyService custodyService;
    private final ItemMovementService movementService;
    private final MovementFlowService flowService;
    private final NewsService newsService;
    private final EventCrudService eventService;
    private final EventRegistrationRepository registrationRepository;
    private final NotificationRepository notificationRepository;

    @Inject
    public DemoClusterSeeder(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            ClusterService clusterService,
            ClusterMemberService memberService,
            ClusterInventoryService clusterInventoryService,
            ClusterProfileFieldService fieldService,
            ClusterStationGroupService stationGroupService,
            ClusterContentService contentService,
            ClusterApplicationService applicationService,
            ClusterStorageQuotaService quotaService,
            StationRepository stationRepository,
            InventoryRepository inventoryRepository,
            InventoryFieldDefinitionService fieldDefinitionService,
            ItemCustodyService custodyService,
            ItemMovementService movementService,
            MovementFlowService flowService,
            NewsService newsService,
            EventCrudService eventService,
            EventRegistrationRepository registrationRepository,
            NotificationRepository notificationRepository) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.clusterService = clusterService;
        this.memberService = memberService;
        this.clusterInventoryService = clusterInventoryService;
        this.fieldService = fieldService;
        this.stationGroupService = stationGroupService;
        this.contentService = contentService;
        this.applicationService = applicationService;
        this.quotaService = quotaService;
        this.stationRepository = stationRepository;
        this.inventoryRepository = inventoryRepository;
        this.fieldDefinitionService = fieldDefinitionService;
        this.custodyService = custodyService;
        this.movementService = movementService;
        this.flowService = flowService;
        this.newsService = newsService;
        this.eventService = eventService;
        this.registrationRepository = registrationRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * After the federation band, because the station left standing outside the cluster is the federation
     * partner: the demo needs one station that stayed out, and inventing a second one purely to be left out
     * would say less than using the one that is already there.
     */
    @Override
    public int order() {
        return FEDERATED_MODULES;
    }

    @Override
    public void seed(DemoRunContext run) {
        Cluster cluster = clusterService.create(
                "Kreisverband Musterstadt", "Der Träger, dem die Wache und ihre Nachbarn angehören");
        seedLogo(cluster);

        // One of the two full stations answers to the association and the other answers to nobody, which is
        // what lets the same feature be looked at both ways. Which one is the profile's to say
        DemoStationContext member = run.clusterStation();
        clusterService.joinStation(cluster.id(), member.stationId());

        // And so does the neighbouring one, so that the screens which reach across a cluster have two
        // stations to reach across rather than one
        joinNeighbour(cluster, run);

        // A station the cluster made itself, which belonged to it from its first moment
        var ownStation = clusterService.createStation(cluster.id(), "Löschzug Nord");

        // The federation partner stays outside and has asked to come in, so the applications screen has
        // something to decide and the standalone case keeps a subject
        seedApplication(cluster, run);

        // The cluster keeps its gear here, which is what lets its own steps appear in a movement
        clusterInventoryService.setUsesInventory(cluster.id(), true);

        ClusterMember admin = seedPeople(cluster, run, member);
        seedRoom(cluster, run, member);
        seedFlows(cluster);
        seedGear(cluster, member);
        seedFields(cluster, member, seedStationGroups(cluster, member, ownStation));
        seedContent(cluster, run, member);
        seedNotifications(cluster, member, admin);

        log.info(
                "Demo: Created cluster {} with home station {} over station {}",
                cluster.id(),
                cluster.homeStationId(),
                member.stationId());
    }

    /**
     * A cluster has no picture of its own: what it shows is the logo of the station it keeps its things on.
     */
    private void seedLogo(Cluster cluster) {
        try {
            var logoBytes = Files.readAllBytes(Path.of("templates", "graphics", "logo.png"));
            stationRepository.updateLogo(cluster.homeStationId(), logoBytes, "image/png");
        } catch (IOException e) {
            log.warn("Demo: Could not set the cluster logo: {}", e.getMessage());
        }
    }

    /**
     * The waiting request, asked for by the station's own owner through the same call the screen makes.
     *
     * <p>Written this way rather than as a row because an application is something a station does: only its
     * owner may ask, and the cluster is told that somebody has. A row put in behind that would leave the
     * demo with a request nobody could take back.
     */
    private void seedApplication(Cluster cluster, DemoRunContext run) {
        var federation = run.federation();
        if (federation == null) {
            log.warn("Demo: No partner station left to ask the cluster for a place");
            return;
        }
        applicationService.apply(cluster.id(), federation.partnerStationId(), federation.partnerMemberId());
    }

    /**
     * Moves the neighbouring station in.
     *
     * <p>Named rather than picked off a list, because which station this is matters: it is the one with
     * members and gear of its own, which is what makes searching across the cluster and releasing a station
     * show anything. The federation partner and the mirror stay outside, the first so the applications
     * screen has something waiting and the second because a mirror is a copy of a station that lives
     * somewhere else.
     */
    private void joinNeighbour(Cluster cluster, DemoRunContext run) {
        var federation = run.federation();
        if (federation == null) {
            log.warn("Demo: No neighbouring station to move into the cluster");
            return;
        }
        clusterService.joinStation(cluster.id(), federation.thirdStationId());
    }

    /**
     * Three people acting for the cluster, so clicking through the screens shows the permissions doing
     * something: an administrator, somebody who only looks after members, and somebody who only looks after
     * gear, the last of them through a group rather than by name.
     *
     * @return the administrator's cluster membership
     */
    private ClusterMember seedPeople(Cluster cluster, DemoRunContext run, DemoStationContext member) {
        ClusterMember admin =
                clusterService.addMember(cluster.id(), run.adminAccount().id(), ClusterUserType.CLUSTER_ADMIN);

        var group = memberService.createGroup(cluster.id(), "Gerätewarte");
        // The whole of looking after gear, not a corner of it: somebody who may correct a size but not
        // answer the step a station is waiting on is not the gear manager the screens talk about.
        memberService.setGroupPermissions(
                cluster.id(), group.id(), Set.of(ClusterPermission.CLUSTER_INVENTORY_MANAGER));

        seedClusterOnlyPerson(cluster);

        List<StationMember> others = otherPeople(member);
        if (!others.isEmpty()) {
            var memberManager =
                    clusterService.addMember(cluster.id(), others.getFirst().accountId(), ClusterUserType.CLUSTER_USER);
            memberService.setPermissions(
                    cluster.id(), memberManager.id(), Set.of(ClusterPermission.CLUSTER_MEMBER_MANAGER));
        }
        if (others.size() > 1) {
            var gearManager =
                    clusterService.addMember(cluster.id(), others.get(1).accountId(), ClusterUserType.CLUSTER_USER);
            memberService.setGroupMembers(cluster.id(), group.id(), Set.of(gearManager.id()));
        }
        return admin;
    }

    /**
     * Somebody whose whole reason to be here is the cluster.
     *
     * <p>Every other cluster role in the demo is held by a person who is also at a station, which hides the
     * case the login has to answer: an account belonging to no station at all lands in its cluster rather
     * than on a station picker with nothing in it.
     */
    private void seedClusterOnlyPerson(Cluster cluster) {
        var account = accountRepository.create("verband@demo.ember", "Verbands", "Leitung", true);
        accountRepository.setUid(account.id(), DemoUids.account("verband@demo.ember"));
        accountRepository.createCredential(account.id(), passwordHasher.hash("demo"));
        clusterService.addMember(cluster.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);
    }

    /**
     * Demo members who can hold a cluster role: everyone with an account of their own, each account once,
     * and not the administrator, who already has one. The station's head member is its administrator, so
     * taking people off the front of a list without this would hand all three roles to one person.
     */
    private static List<StationMember> otherPeople(DemoStationContext station) {
        int owner = station.adminMember().accountId();
        Set<Integer> seen = new HashSet<>();
        return Stream.concat(station.members().betreuer().stream(), station.members().fortgeschritten().stream())
                .filter(member -> member.accountId() != null && member.accountId() != owner)
                .filter(member -> seen.add(member.accountId()))
                .toList();
    }

    /**
     * The room the cluster hands out, in every place a station can get its numbers from.
     *
     * <p>The instance grants a pool, the cluster says what a station it has decided nothing about may keep,
     * and it keeps two tiers to hand out. The four stations then sit in four different places on that
     * screen on purpose: its own store on numbers set by hand, the demo station on the larger tier, the
     * neighbour on the smaller one, and the station it made itself on nothing at all, which is the row that
     * reads as inherited.
     *
     * <p>Every number here is at or above what the instance configuration gives a station on its own, so
     * joining this cluster never costs a demo station room it had.
     */
    private void seedRoom(Cluster cluster, DemoRunContext run, DemoStationContext member) {
        quotaService.setStoragePool(cluster.id(), 100 * GIB);
        quotaService.setDefaults(new ClusterQuotaDefaults(
                cluster.id(), 8 * GIB, 6 * GIB, 3 * GIB, 2 * GIB, 1 * GIB, 100 * MIB, 8 * MIB));

        var small = quotaService.createPreset(
                cluster.id(), "Kleine Wache", 6 * GIB, 4 * GIB, 2 * GIB, 1 * GIB, 1 * GIB, 50 * MIB, 5 * MIB);
        var large = quotaService.createPreset(
                cluster.id(), "Große Wache", 25 * GIB, 15 * GIB, 6 * GIB, 3 * GIB, 2 * GIB, 200 * MIB, 20 * MIB);

        // The cluster's own files live on the station it owns, and they are no freer than anybody else's:
        // its store is granted a total like every other station and counts against the same pool
        stationRepository
                .findById(cluster.homeStationId())
                .ifPresent(home -> quotaService.setGrant(
                        cluster.id(),
                        home.uid(),
                        new ClusterStorageQuotaService.Dimensions(10 * GIB, null, null, null, null, null, null)));

        quotaService.applyPreset(
                cluster.id(), large.id(), List.of(member.station().uid()));

        var federation = run.federation();
        if (federation != null) {
            stationRepository
                    .findById(federation.thirdStationId())
                    .ifPresent(
                            neighbour -> quotaService.applyPreset(cluster.id(), small.id(), List.of(neighbour.uid())));
        }
    }

    /**
     * The two chains the cluster's gear walks, each carrying the owner steps only the cluster can answer.
     *
     * <p>Written out rather than taken from a preset on purpose: the presets carry no owner steps at all,
     * because they are what a station falls back to when nothing above it can answer for itself.
     */
    private void seedFlows(Cluster cluster) {
        var exchange = clusterInventoryService.createFlow(
                cluster.id(), "Tausch über den Kreisverband", MovementPurpose.EXCHANGE);
        flowService.addStep(
                exchange.id(),
                "Mitglied meldet an",
                StepActor.MEMBER,
                StepSubject.OUTGOING,
                ItemCustody.WITH_MEMBER,
                false);
        flowService.addStep(
                exchange.id(),
                "Wache nimmt zurück",
                StepActor.STATION,
                StepSubject.OUTGOING,
                ItemCustody.AT_STATION,
                false);
        flowService.addStep(
                exchange.id(),
                "Wache schickt weg",
                StepActor.STATION,
                StepSubject.OUTGOING,
                ItemCustody.IN_TRANSIT,
                false);
        flowService.addStep(
                exchange.id(),
                "Verband nimmt an",
                StepActor.OWNER,
                StepSubject.OUTGOING,
                ItemCustody.WITH_OWNER,
                false);
        flowService.addStep(
                exchange.id(),
                "Verband schickt Ersatz",
                StepActor.OWNER,
                StepSubject.INCOMING,
                ItemCustody.IN_TRANSIT,
                true);
        flowService.addStep(
                exchange.id(),
                "Wache gibt aus",
                StepActor.STATION,
                StepSubject.INCOMING,
                ItemCustody.WITH_MEMBER,
                false);

        var returning = clusterInventoryService.createFlow(
                cluster.id(), "Rückgabe an den Kreisverband", MovementPurpose.RETURN);
        flowService.addStep(
                returning.id(),
                "Wache schickt zurück",
                StepActor.STATION,
                StepSubject.OUTGOING,
                ItemCustody.IN_TRANSIT,
                false);
        flowService.addStep(
                returning.id(),
                "Verband nimmt an",
                StepActor.OWNER,
                StepSubject.OUTGOING,
                ItemCustody.WITH_OWNER,
                false);

        // Sending gear out starts on the cluster's own step, which is what puts a consignment in the post
        // rather than having it arrive the moment it was sent.
        var sending = clusterInventoryService.createFlow(cluster.id(), "Ausgabe an eine Wache", MovementPurpose.ISSUE);
        flowService.addStep(
                sending.id(), "Verband schickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT, true);
        flowService.addStep(
                sending.id(), "Wache nimmt an", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, false);
    }

    /**
     * A pool of the cluster's own gear spread across the custody states, so each of them is visible rather
     * than described, plus one piece whose owner is not on this instance at all.
     */
    private void seedGear(Cluster cluster, DemoStationContext member) {
        var pool = inventoryRepository.create(cluster.homeStationId(), "Einsatzkleidung", InventoryType.EXTERNAL, true);
        inventoryRepository.createSize(pool.id(), "48", 0, null);
        inventoryRepository.createSize(pool.id(), "50", 1, null);
        var sizes = inventoryRepository.findSizes(pool.id());
        Integer smallId = sizes.isEmpty() ? null : sizes.getFirst().id();
        Integer largeId = sizes.size() > 1 ? sizes.get(1).id() : smallId;

        // Two questions the cluster asks about each piece, so its gear carries more than a name
        fieldDefinitionService.create(
                pool.id(),
                "hersteller",
                "Hersteller",
                FieldType.TEXT,
                false,
                0,
                fieldDefinitionService.defaultConfig(FieldType.TEXT));
        fieldDefinitionService.create(
                pool.id(),
                "naechste_pruefung",
                "Nächste Prüfung",
                FieldType.DATE,
                false,
                1,
                fieldDefinitionService.defaultConfig(FieldType.DATE));

        // Resting in the cluster's own store, which is where gear waits before it is sent anywhere
        var spareJacket = inventoryRepository.createItem(
                pool.id(), "KV-0001", "Einsatzjacke", smallId, null, ItemOwner.CLUSTER, cluster.id());
        var spareTrousers = inventoryRepository.createItem(
                pool.id(), "KV-0002", "Einsatzhose", smallId, null, ItemOwner.CLUSTER, cluster.id());
        custodyService.returnToOwner(spareJacket.id());
        custodyService.returnToOwner(spareTrousers.id());

        // Out at the demo station, on a shelf
        var atStation = inventoryRepository.createItem(
                pool.id(), "KV-0003", "Einsatzjacke", smallId, null, ItemOwner.CLUSTER, cluster.id());
        custodyService.applyStepCustody(atStation.id(), ItemCustody.AT_STATION, null, null, member.stationId());

        // The requirement hangs off the cluster's own inventory, so there is one definition rather than one
        // per station that would have to be kept matching by hand
        inventoryRepository.createRequirement(pool.id(), StationUserType.MEMBER, 0, null, 1);

        seedMovements(cluster, member, pool.id(), smallId, largeId);

        // A piece whose owner is not on this instance, written down after the station joined. What the
        // association owns was adopted on the way in and this was not, which is the difference the record
        // exists to keep: the station stands in for an owner that cannot answer for itself
        inventoryRepository.findByStation(member.stationId()).stream()
                .filter(inventory -> "Gemeindematerial".equals(inventory.name()))
                .findFirst()
                .ifPresent(municipal -> inventoryRepository.createItem(
                        municipal.id(), "GM-0002", "Anhänger der Gemeinde", null, null, ItemOwner.CLUSTER, null));
    }

    /**
     * One return and one exchange, each walked as far as the station can walk it and then left standing on
     * the step the cluster owns. That is the state the whole model exists for: the station has done its part
     * and cannot do the next one, the gear is in the post, and the cluster is the one being waited on.
     */
    private void seedMovements(Cluster cluster, DemoStationContext member, int poolId, Integer small, Integer large) {
        var head = member.members().head();
        if (head == null) return;

        var goingBack = inventoryRepository.createItem(
                poolId, "KV-0005", "Einsatzhose", small, null, ItemOwner.CLUSTER, cluster.id());
        custodyService.applyStepCustody(goingBack.id(), ItemCustody.AT_STATION, null, null, member.stationId());
        movementService.create(
                member.stationId(),
                MovementPurpose.RETURN,
                head.id(),
                nameOf(head),
                goingBack.id(),
                poolId,
                null,
                null,
                "Wird an der Wache nicht mehr gebraucht",
                new ItemMovementService.Actor(head.id(), true, false),
                null);

        // One piece that simply stays with the person, so the ordinary case is on screen too
        var worn = inventoryRepository.createItem(
                poolId, "KV-0004", "Einsatzjacke", large, null, ItemOwner.CLUSTER, cluster.id());
        custodyService.applyStepCustody(worn.id(), ItemCustody.AT_STATION, null, null, member.stationId());
        custodyService.assignToMember(worn.id(), head.id(), nameOf(head));

        List<StationMember> others = otherPeople(member);
        if (others.isEmpty()) return;
        StationMember wearer = others.getFirst();

        var tooSmall = inventoryRepository.createItem(
                poolId, "KV-0006", "Einsatzjacke", small, null, ItemOwner.CLUSTER, cluster.id());
        custodyService.applyStepCustody(tooSmall.id(), ItemCustody.AT_STATION, null, null, member.stationId());
        custodyService.assignToMember(tooSmall.id(), wearer.id(), nameOf(wearer));

        var actor = new ItemMovementService.Actor(wearer.id(), true, false);
        var exchange = movementService.create(
                member.stationId(),
                MovementPurpose.EXCHANGE,
                wearer.id(),
                nameOf(wearer),
                tooSmall.id(),
                poolId,
                small,
                large,
                "Jacke spannt an den Schultern",
                actor,
                null);
        // The station takes it back and puts it in the post, and there its part ends
        exchange = movementService.acknowledge(exchange.id(), exchange.currentStepId(), actor, "", null);
        movementService.acknowledge(exchange.id(), exchange.currentStepId(), actor, "", null);
    }

    /**
     * Two ways the cluster files its stations, because a demo with one teaches that a station belongs to
     * exactly one group, which is the thing that is not true.
     *
     * @return the group the targeted question is asked of
     */
    private int seedStationGroups(Cluster cluster, DemoStationContext member, Station ownStation) {
        var breathing = stationGroupService.create(cluster.id(), "Atemschutzwachen");
        var north = stationGroupService.create(cluster.id(), "Nordkreis");

        stationGroupService.setStations(cluster.id(), breathing.id(), List.of(uidOf(member.stationId())));
        stationGroupService.setStations(cluster.id(), north.id(), List.of(uidOf(member.stationId()), ownStation.uid()));
        return breathing.id();
    }

    private UUID uidOf(int stationId) {
        return stationRepository.findById(stationId).orElseThrow().uid();
    }

    /**
     * Two questions the cluster asks, both answered. One is asked of every station and one only of the
     * stations that carry breathing apparatus, because a demo where everybody is asked everything shows
     * nothing of what a group of stations is for.
     */
    private void seedFields(Cluster cluster, DemoStationContext member, int breathingGroupId) {
        var licence = fieldService.create(
                cluster.id(),
                "Führerscheinklasse",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);
        var breathing = fieldService.create(
                cluster.id(),
                "Atemschutztauglich",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.empty(),
                1,
                ProfileFieldScope.MEMBER,
                false,
                true,
                breathingGroupId);

        var head = member.members().head();
        if (head != null) {
            fieldService.setValues(
                    cluster.id(), head.id(), Map.of(licence.id(), "\"C1\"", breathing.id(), "true"), head.id());
        }
    }

    /**
     * Something for the cluster to have said: a folder with an article in it, one news entry and one
     * appointment, all of which reach every member station without anybody sharing them. Somebody from a
     * member station has signed up for the appointment, which is the point of an event held above the
     * stations rather than at one of them.
     */
    private void seedContent(Cluster cluster, DemoRunContext run, DemoStationContext member) {
        int accountId = run.adminAccount().id();

        var folder = contentService.createFolder(
                cluster.id(), null, "Dienstanweisungen", "Gilt für alle Wachen des Kreisverbands", accountId);
        contentService.createArticle(
                cluster.id(),
                folder.id(),
                "Umgang mit Einsatzkleidung",
                "Pflege, Prüfung und Tausch",
                "# Umgang mit Einsatzkleidung\n\nJede Jacke wird jährlich geprüft.",
                accountId);

        newsService.create(
                cluster.homeStationId(),
                "Kreisübung im Herbst",
                "Alle Wachen des Kreisverbands sind eingeladen.",
                contentService.authorIdentity(cluster.id(), accountId),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        var drill = eventService.create(
                cluster.homeStationId(),
                "Kreisübung",
                "Gemeinsame Übung aller Wachen des Kreisverbands",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(4, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var head = member.members().head();
        if (head != null) {
            registrationRepository.create(
                    drill.id(),
                    head.id(),
                    LocalDate.ofInstant(start, ZoneOffset.UTC),
                    RegistrationStatus.ACCEPTED,
                    null);
        }
    }

    /**
     * One of every notification a cluster can cause, so the lists have something in them before anybody has
     * done anything. Each goes where that notification really goes: the ones about running a cluster to the
     * administrator's cluster membership, the ones about being in one to their membership at the station,
     * because those are two different inboxes belonging to the same person.
     */
    private void seedNotifications(Cluster cluster, DemoStationContext member, ClusterMember admin) {
        String name = cluster.name();

        notificationRepository.createForClusterMember(
                admin.id(),
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                NotificationData.of(new NotificationParams.ClusterApplicationSubmitted("Feuerwehr Nachbardorf")));
        notificationRepository.createForClusterMember(
                admin.id(),
                NotificationType.CLUSTER_APPLICATION_WITHDRAWN,
                NotificationData.of(new NotificationParams.ClusterApplicationWithdrawn("Feuerwehr Süd")));
        notificationRepository.createForClusterMember(
                admin.id(),
                NotificationType.CLUSTER_MEMBER_ROLE_CHANGED,
                NotificationData.of(new NotificationParams.ClusterMemberRoleChanged(name)));

        StationMember adminMember = member.adminMember();
        if (adminMember == null) return;
        int memberId = adminMember.id();

        notificationRepository.create(
                memberId,
                NotificationType.CLUSTER_APPLICATION_APPROVED,
                NotificationData.of(new NotificationParams.ClusterApplicationApproved(name)));
        notificationRepository.create(
                memberId,
                NotificationType.CLUSTER_APPLICATION_DENIED,
                NotificationData.of(new NotificationParams.ClusterApplicationDenied(
                        name, "Bitte im nächsten Jahr erneut anfragen")));
        notificationRepository.create(
                memberId,
                NotificationType.CLUSTER_STATION_RELEASED,
                NotificationData.of(new NotificationParams.ClusterStationReleased(name)));
        notificationRepository.create(
                memberId,
                NotificationType.CLUSTER_MODULE_DENIED,
                NotificationData.of(new NotificationParams.ClusterModuleDenied(name, "Fundsachen")));
        notificationRepository.create(
                memberId,
                NotificationType.CLUSTER_QUOTA_CHANGED,
                NotificationData.of(new NotificationParams.ClusterQuotaChanged(name, "5 GB")));
        notificationRepository.create(
                memberId,
                NotificationType.CLUSTER_FIELD_VALUE_CHANGED,
                NotificationData.of(new NotificationParams.ClusterFieldValueChanged(name, "Führerscheinklasse")));
    }

    private static String nameOf(StationMember member) {
        return member.displayName() != null ? member.displayName() : "";
    }
}
