/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.quiz.service.QuizTestService;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

/**
 * The states the explainer videos need to show, put there on purpose rather than left to chance.
 *
 * <p>Most of what the demo holds is seeded with a fixed random seed, which makes it the same after every
 * reset but says nothing about who ends up with what. A video that has to show a running exchange cannot
 * work with "somebody has one": it has to be the person the camera is logged in as. The same goes for the
 * three states an absence can be in, and for a deadline close enough that the reminder is worth showing.
 *
 * <p>Runs last, so everything it hangs things on already exists.
 */
@Singleton
public class DemoVideoSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoVideoSeeder.class);

    private final AttendanceRepository attendanceRepository;
    private final InventoryRepository inventoryRepository;
    private final ExchangeService exchangeService;
    private final EventCrudService crudService;
    private final StationMailProviderRepository mailProviderRepository;
    private final LostAndFoundService lostAndFoundService;
    private final LostAndFoundImageService lostAndFoundImageService;
    private final FormRepository formRepository;
    private final QuizTestRepository quizTestRepository;
    private final QuizTestService quizTestService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public DemoVideoSeeder(
            AttendanceRepository attendanceRepository,
            InventoryRepository inventoryRepository,
            ExchangeService exchangeService,
            EventCrudService crudService,
            StationMailProviderRepository mailProviderRepository,
            LostAndFoundService lostAndFoundService,
            LostAndFoundImageService lostAndFoundImageService,
            FormRepository formRepository,
            QuizTestRepository quizTestRepository,
            QuizTestService quizTestService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository) {
        this.attendanceRepository = attendanceRepository;
        this.inventoryRepository = inventoryRepository;
        this.exchangeService = exchangeService;
        this.crudService = crudService;
        this.mailProviderRepository = mailProviderRepository;
        this.lostAndFoundService = lostAndFoundService;
        this.lostAndFoundImageService = lostAndFoundImageService;
        this.formRepository = formRepository;
        this.quizTestRepository = quizTestRepository;
        this.quizTestService = quizTestService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public int order() {
        return VIDEO_CASES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        if (members == null || members.eltern().isEmpty() || members.anfaenger().isEmpty()) return;

        StationMember guardian = members.eltern().getFirst();
        StationMember kid = members.anfaenger().getFirst();

        seedAbsences(guardian, kid);
        seedExchange(station.stationId(), kid);
        seedRegistrationEvent(station.stationId());
        seedMailProvider(station);
        seedLostAndFoundPictures(station.stationId());
        seedOpenTasks(station.stationId());
        seedInvitedMember(station);
        seedTodaysEvent(station.stationId());
        seedExchangeStages(station);
        seedSwapWaitingToBeHandedOver(station, kid);
        grantAssignRight(station);
    }

    /**
     * A swap of the first Anfänger's whose replacement is at the station, so somebody is owed a piece
     * they can be handed on the spot.
     *
     * <p>Placed on that member on purpose: the lost and found already leaves them a claimed item, so
     * one name on the attendance sheet carries both kinds of note and a story about the notes has one
     * place to look. {@link #seedExchangeStages} also reaches this stage, but on whichever
     * Fortgeschrittener happens to have a free piece, which is nothing a test can be pointed at.
     */
    private void seedSwapWaitingToBeHandedOver(DemoStationContext station, StationMember kid) {
        if (station.members().betreuer().isEmpty()) return;
        int actor = station.members().betreuer().getFirst().id();

        var moving = inventoryRepository.findMovingItemsOfMember(kid.id());
        var item = inventoryRepository.findItemsByMember(kid.id()).stream()
                .filter(candidate -> candidate.ownerKind() != ItemOwner.CLUSTER)
                .filter(candidate -> !moving.containsKey(candidate.id()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            log.info("Demo: no free piece to build a waiting handover on, station {}", station.stationId());
            return;
        }

        var exchange = exchangeService.create(
                station.stationId(),
                kid.id(),
                "Demo User",
                item.id(),
                item.inventoryId(),
                item.sizeId(),
                item.sizeId(),
                "Zu klein geworden",
                null);
        exchangeService.updateStatus(exchange.id(), ExchangeStatus.ARRIVED, actor, "Ersatz liegt bereit");
        log.info(
                "Demo: swap {} for member {} waits to be handed over, station {}",
                exchange.id(),
                kid.id(),
                station.stationId());
    }

    /**
     * A date today with an attendance template on it, so the recording can start the roll call the way it
     * really begins: from the date, not from a template picked by hand.
     *
     * <p>The demo's own weekly dates only fall on today one day in seven, which is no use to somebody who
     * films on a Wednesday.
     */
    private void seedTodaysEvent(int stationId) {
        var template = attendanceRepository.findTemplatesByStation(stationId).stream()
                .findFirst()
                .orElse(null);
        LocalDate today = LocalDate.now();
        crudService.create(
                stationId,
                "Dienstabend",
                "Der Abend, an dem die Anwesenheit erfasst wird",
                StationEvent.EventType.ONE_TIME,
                null,
                today.atTime(17, 30).toInstant(ZoneOffset.UTC),
                today.atTime(19, 30).toInstant(ZoneOffset.UTC),
                template != null ? template.id() : null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        log.info("Demo: A date today carries an attendance template, station {}", stationId);
    }

    /**
     * One exchange on each of the stages a manager moves them through, because the stages only explain
     * themselves side by side. A list where everything says the same thing teaches nothing.
     */
    private void seedExchangeStages(DemoStationContext station) {
        var members = station.members();
        var kids = new ArrayList<>(members.fortgeschritten());
        if (kids.isEmpty() || members.betreuer().isEmpty()) return;
        int actor = members.betreuer().getFirst().id();

        var wanted = List.of(ExchangeStatus.RECEIVED, ExchangeStatus.SHIPPED, ExchangeStatus.ARRIVED);
        int made = 0;
        for (var kid : kids) {
            if (made >= wanted.size()) break;
            // A piece the association owns is moved by the association: one of the steps belongs to
            // the OWNER, and a Betreuer acknowledging it is refused. Only the station's own gear can
            // be walked through the stages from here.
            var moving = inventoryRepository.findMovingItemsOfMember(kid.id());
            var item = inventoryRepository.findItemsByMember(kid.id()).stream()
                    .filter(candidate -> candidate.ownerKind() != ItemOwner.CLUSTER)
                    .filter(candidate -> !moving.containsKey(candidate.id()))
                    .findFirst()
                    .orElse(null);
            if (item == null) continue;
            var exchange = exchangeService.create(
                    station.stationId(),
                    kid.id(),
                    "Demo User",
                    item.id(),
                    item.inventoryId(),
                    item.sizeId(),
                    item.sizeId(),
                    "Passt nicht mehr",
                    null);
            exchangeService.updateStatus(exchange.id(), wanted.get(made), actor, "Für die Aufnahme gestellt");
            made++;
        }
        log.info("Demo: {} exchange(s) on different stages, station {}", made, station.stationId());
    }

    /**
     * The right to hand equipment out, given to the account the team videos are recorded with.
     *
     * <p>It stays off for every role by default, which is the point the video makes. Somebody has to hold it
     * for the page to exist at all, and on a demo that somebody is whoever runs the station.
     */
    private void grantAssignRight(DemoStationContext station) {
        var members = station.members();
        if (members.betreuer().isEmpty()) return;
        int member = members.betreuer().getFirst().id();
        stationMemberRepository
                .findPermissionByName(StationPermission.INVENTORY_ASSIGN)
                .ifPresent(role -> {
                    stationMemberRepository.grantPermission(member, role.id());
                    log.info("Demo: Member {} may hand equipment out, station {}", member, station.stationId());
                });
    }

    /**
     * The token the waiting invitation carries, fixed rather than random.
     *
     * <p>A random one would have to be fished out of the database before every take, because no mail is
     * delivered locally. Fixed, it can simply be typed into the postbox prop once and stay there. Each
     * station gets its own, because the seeder runs once per station and a token is unique across the
     * instance.
     */
    public static final String INVITE_TOKEN = "demo-video-einladung";

    /**
     * The invitation token of one station.
     *
     * @param station the station being seeded
     * @return the token to type into the postbox prop for that station
     */
    public static String inviteToken(DemoStationContext station) {
        return INVITE_TOKEN + station.profile().addressSuffix();
    }

    /**
     * Somebody who has been taken on but has never set a password, so the way from the invitation to the
     * first login can be walked in front of the camera instead of being described.
     *
     * <p>The address counts as verified, exactly as one taken on through the invite path does. Login refuses
     * an unverified address and setting a password does not verify one, so leaving it unverified would stop
     * the journey one step short of the point it is making.
     */
    private void seedInvitedMember(DemoStationContext station) {
        String address = "nele@sommer" + station.profile().addressSuffix() + ".local";
        if (accountRepository.findByEmail(address).isPresent()) return;

        var account = accountRepository.create(address, "Nele", "Sommer", true, station.stationId());
        accountRepository.setUid(account.id(), DemoUids.account(address));
        var member = stationMemberRepository.create(station.stationId(), account.id());
        stationMemberRepository.setUserType(member.id(), StationUserType.MEMBER);
        stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(role -> stationMemberRepository.grantPermission(member.id(), role.id()));

        String token = inviteToken(station);
        accountRepository.createToken(
                account.id(), token, TokenType.SET_PASSWORD, Instant.now().plus(Duration.ofDays(3650)));
        log.info("Demo: {} was invited and still owes a password, token '{}'", address, token);
    }

    /**
     * One survey and one test that are actually required, so the page of open tasks has something on it.
     *
     * <p>Nothing here is new: the demo already writes both, only never as something anybody has to do.
     * A page whose whole point is "you still owe us this" cannot be filmed while it is empty.
     */
    private void seedOpenTasks(int stationId) {
        formRepository.findByStation(stationId).stream()
                .filter(form -> form.status() == Form.FormStatus.OPEN && !form.forced())
                .findFirst()
                .ifPresent(form -> {
                    formRepository.update(
                            form.id(),
                            form.title(),
                            form.description(),
                            form.shuffleQuestions(),
                            form.allowEdit(),
                            true,
                            form.startAt(),
                            form.endAt());
                    log.info("Demo: Survey {} is required, so it shows up under open tasks", form.id());
                });

        quizTestRepository.findByStation(stationId).stream()
                .filter(test -> test.status() == TestStatus.ACTIVE && !test.forced())
                .findFirst()
                .ifPresent(test -> {
                    quizTestService.updateTest(
                            test.id(),
                            test.title(),
                            test.description(),
                            test.timeLimit(),
                            test.shuffle(),
                            true,
                            test.startAt(),
                            test.endAt());
                    log.info("Demo: Test {} is required, so it shows up under open tasks", test.id());
                });
    }

    /**
     * One absence in each of the three states a member ever sees, plus one entered on behalf of a
     * managed member so the guardian's side of the page is not empty either.
     */
    private void seedAbsences(StationMember guardian, StationMember kid) {
        LocalDate today = LocalDate.now();
        attendanceRepository.createAbsence(
                guardian.id(), today.minusDays(24), today.minusDays(17), "Sommerurlaub", null);
        attendanceRepository.createAbsence(guardian.id(), today.minusDays(1), today.plusDays(4), "Krank", null);
        attendanceRepository.createAbsence(
                guardian.id(), today.plusDays(21), today.plusDays(28), "Betriebsausflug", null);
        attendanceRepository.createAbsence(
                kid.id(), today.plusDays(10), today.plusDays(17), "Klassenfahrt", guardian.id());
        log.info("Demo: Absences for the videos on member {} and {}", guardian.id(), kid.id());
    }

    /**
     * A running exchange on the member the camera is logged in as, so the list of open movements is
     * never empty when it is filmed.
     *
     * <p>A piece something is already running on is passed over: it can only be on one movement at a
     * time, and an earlier seeder may well have set one going on it.
     */
    private void seedExchange(int stationId, StationMember kid) {
        var moving = inventoryRepository.findMovingItemsOfMember(kid.id());
        var item = inventoryRepository.findItemsByMember(kid.id()).stream()
                .filter(candidate -> !moving.containsKey(candidate.id()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            log.warn("Demo: No free item assigned to member {}, no exchange for the videos", kid.id());
            return;
        }
        exchangeService.create(
                stationId,
                kid.id(),
                "Demo User",
                item.id(),
                item.inventoryId(),
                item.sizeId(),
                item.sizeId(),
                "Die Jacke ist zu eng geworden",
                null);
        log.info("Demo: Running exchange for the videos on member {}", kid.id());
    }

    /**
     * Two things one screen cannot otherwise show side by side: a date that has to be answered and a
     * deadline near enough that the reminder before it is worth filming.
     */
    private void seedRegistrationEvent(int stationId) {
        LocalDate day = LocalDate.now().plusDays(9);
        crudService.create(
                stationId,
                "Ausflug zur Feuerwache",
                "Begrenzte Plätze, deshalb mit Anmeldung",
                StationEvent.EventType.ONE_TIME,
                null,
                day.atTime(9, 0).toInstant(ZoneOffset.UTC),
                day.atTime(16, 0).toInstant(ZoneOffset.UTC),
                null,
                true,
                LocalDate.now().plusDays(2).atTime(23, 59).toInstant(ZoneOffset.UTC),
                false,
                null,
                20,
                null,
                null,
                null);
        log.info("Demo: Event with registration and a near deadline for the videos");
    }

    /**
     * Outbound mail on the second station and none on the first, because the notification settings
     * read differently depending on it and a video has to show both. Nothing is ever sent: the host
     * is the address a local mail catcher answers on, and there is none unless somebody starts one.
     */
    private void seedMailProvider(DemoStationContext station) {
        if (!station.profile().joinsCluster()) return;
        mailProviderRepository.replace(
                station.stationId(),
                List.of(new MailChainEntry(
                        0,
                        MailProviderType.SMTP,
                        "localhost",
                        1025,
                        false,
                        "demo",
                        "demo",
                        "",
                        "post@" + station.profile().key() + ".local",
                        station.station().name(),
                        1,
                        200,
                        "Lokaler Mailfänger",
                        "")));
        log.info("Demo: Station {} has outbound mail, the other one has none", station.stationId());
    }

    /**
     * A drawing for every item in the lost-and-found, because a page of grey placeholders says nothing
     * about a page that is meant to help somebody recognise their own jacket.
     *
     * <p>Drawn rather than shipped: a handful of strokes costs nothing, survives a repository that keeps
     * no binaries, and needs no network the way the seeded portraits do.
     */
    private void seedLostAndFoundPictures(int stationId) {
        for (var item : lostAndFoundService.findByStation(stationId)) {
            String what = item.description().toLowerCase(Locale.ROOT);
            Consumer<Graphics2D> shape;
            if (what.contains("helm")) shape = DemoVideoSeeder::drawHelmet;
            else if (what.contains("flasche")) shape = DemoVideoSeeder::drawBottle;
            else shape = DemoVideoSeeder::drawJacket;
            try {
                lostAndFoundImageService.store(stationId, item.id(), sketch(shape), "image/png", 0);
            } catch (IOException e) {
                log.warn("Demo: No drawing for lost-and-found item {}", item.id(), e);
            }
        }
        log.info("Demo: Drawings on the lost-and-found items of station {}", stationId);
    }

    private static final int SKETCH = 640;

    /** A drawing on paper: warm ground, one dark stroke, nothing filled in. */
    private static byte[] sketch(Consumer<Graphics2D> shape) throws IOException {
        var image = new BufferedImage(SKETCH, SKETCH, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(new Color(0xF7, 0xF4, 0xEF));
            g.fillRect(0, 0, SKETCH, SKETCH);
            g.setColor(new Color(0x2B, 0x2B, 0x2B));
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            shape.accept(g);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void drawJacket(Graphics2D g) {
        var body = new GeneralPath(Path2D.WIND_NON_ZERO);
        body.moveTo(230, 150);
        body.lineTo(180, 185);
        body.lineTo(130, 320);
        body.lineTo(190, 345);
        body.lineTo(215, 265);
        body.lineTo(215, 500);
        body.lineTo(425, 500);
        body.lineTo(425, 265);
        body.lineTo(450, 345);
        body.lineTo(510, 320);
        body.lineTo(460, 185);
        body.lineTo(410, 150);
        body.closePath();
        g.draw(body);

        var collar = new GeneralPath();
        collar.moveTo(230, 150);
        collar.lineTo(320, 215);
        collar.lineTo(410, 150);
        g.draw(collar);

        g.drawLine(320, 215, 320, 500);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int y = 260; y <= 460; y += 50) {
            g.drawLine(305, y, 335, y);
        }
        g.draw(new Ellipse2D.Double(258, 392, 34, 34));
        g.draw(new Ellipse2D.Double(348, 392, 34, 34));
    }

    private static void drawHelmet(Graphics2D g) {
        var shell = new GeneralPath();
        shell.moveTo(150, 400);
        shell.curveTo(150, 210, 490, 210, 490, 400);
        g.draw(shell);

        var brim = new GeneralPath();
        brim.moveTo(120, 400);
        brim.curveTo(180, 445, 460, 445, 520, 400);
        brim.curveTo(460, 425, 180, 425, 120, 400);
        brim.closePath();
        g.draw(brim);

        g.drawLine(320, 268, 320, 400);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        var badge = new GeneralPath();
        badge.moveTo(320, 262);
        badge.lineTo(352, 300);
        badge.lineTo(320, 338);
        badge.lineTo(288, 300);
        badge.closePath();
        g.draw(badge);
        g.draw(new Ellipse2D.Double(232, 372, 24, 24));
        g.draw(new Ellipse2D.Double(384, 372, 24, 24));
    }

    private static void drawBottle(Graphics2D g) {
        var body = new GeneralPath();
        body.moveTo(255, 235);
        body.lineTo(255, 480);
        body.curveTo(255, 520, 385, 520, 385, 480);
        body.lineTo(385, 235);
        g.draw(body);

        g.drawRect(268, 168, 104, 66);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(268, 200, 372, 200);
        g.drawRect(276, 300, 88, 88);
        var flame = new GeneralPath();
        flame.moveTo(320, 322);
        flame.curveTo(348, 346, 344, 372, 320, 372);
        flame.curveTo(296, 372, 292, 346, 320, 322);
        flame.closePath();
        g.draw(flame);
    }
}
