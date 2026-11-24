package com.meditationhall.scheduler

import com.meditationhall.db.Attendance
import com.meditationhall.db.CommunityPresence
import com.meditationhall.db.HallMemberships
import com.meditationhall.db.HallSchedules
import com.meditationhall.db.Halls
import com.meditationhall.db.MeditationLogs
import com.meditationhall.db.MeditationSessions
import com.meditationhall.db.Users
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.services.ScheduleMath
import com.meditationhall.services.ScheduleSpec
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.concurrent.thread
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

/**
 * Demo meditators spread across time zones. They appear in the Open Hall during
 * their own local sit, and in a scheduled hall while that hall's window is open.
 * Presence older than 90 seconds is dropped, so this loop refreshes every 25 seconds.
 * Set NETWORK_SIM=false to leave the rows in place without heartbeating them.
 */
object NetworkPresence {
    private val log = LoggerFactory.getLogger(NetworkPresence::class.java)
    private val hostId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c0")
    private val nitinId: UUID = UUID.fromString("6bc7a786-0a10-45bb-a864-818385c64708")

    fun start() {
        if (System.getenv("NETWORK_SIM") == "false") {
            log.info("network presence simulator disabled")
            return
        }
        thread(isDaemon = true, name = "network-presence") {
            while (true) {
                runCatching { tick() }
                    .onFailure { log.warn("network presence tick failed", it) }
                Thread.sleep(25_000)
            }
        }
    }

    private data class Person(
        val id: UUID,
        val name: String,
        val zone: String,
        val email: String,
        val sitHour: Int,
        val sitMinute: Int,
        val durationMinutes: Int
    )

    private data class HallSeed(
        val id: UUID,
        val name: String,
        val description: String,
        val zone: String,
        val start: String,
        val type: String,
        val days: String,
        val durationMinutes: Int,
        val shareCode: String,
        val memberZones: Set<String>,
        val ownerId: UUID? = null
    )

    private fun people(): List<Person> {
        val roster = listOf(
            "Asia/Tokyo" to listOf(
                "Yuki Tanaka", "Haruto Sato", "Aoi Nakamura", "Ren Suzuki",
                "Hana Kobayashi", "Sota Watanabe", "Mei Ito", "Kaito Yamamoto"
            ),
            "Asia/Seoul" to listOf(
                "Seo-yeon Kim", "Min-jun Park", "Ji-woo Choi", "Ha-eun Jung",
                "Do-yun Kang", "Seo-jun Cho", "Ye-jun Yoon", "Ha-rin Lim"
            ),
            "Asia/Kolkata" to listOf(
                "Ananya Sharma", "Arjun Mehta", "Priya Iyer", "Rohan Das",
                "Kavya Nair", "Vikram Singh", "Meera Joshi", "Aditi Rao"
            ),
            "Asia/Bangkok" to listOf(
                "Mali Srisawat", "Niran Chaiyaporn", "Ploy Thongchai", "Somchai Wattana",
                "Anong Boonmee", "Krit Suksawat", "Dao Phanit", "Lek Charoen"
            ),
            "Africa/Nairobi" to listOf(
                "Amina Otieno", "Juma Mwangi", "Wanjiku Kamau", "Baraka Ochieng",
                "Zuri Njeri", "Kipchoge Langat", "Achieng Odhiambo", "Hassan Abdi"
            ),
            "Europe/London" to listOf(
                "Amira Khan", "Oliver Hughes", "Freya Clarke", "Jonah Adeyemi",
                "Isla Murphy", "Priya Shah", "Callum Brooks", "Hannah Walsh"
            ),
            "Europe/Berlin" to listOf(
                "Lena Hoffmann", "Jonas Becker", "Fatima Yilmaz", "Emil Wagner",
                "Nora Schneider", "Lukas Krüger", "Sofia Klein", "Mehmet Demir"
            ),
            "America/Sao_Paulo" to listOf(
                "Ana Souza", "Lucas Ferreira", "Mariana Oliveira", "Pedro Almeida",
                "Camila Rocha", "Gabriel Costa", "Beatriz Lima", "Rafael Nunes"
            ),
            "America/New_York" to listOf(
                "Maya Johnson", "Ethan Brooks", "Sofia Alvarez", "Noah Williams",
                "Aisha Rahman", "Liam O'Connor", "Chloe Bennett", "Jacob Cohen"
            ),
            "America/Los_Angeles" to listOf(
                "Elena Cruz", "Noah Park", "Aria Patel", "Mateo Reyes",
                "Hana Kim", "Julian Flores", "Leila Haddad", "Owen Chen"
            ),
            "America/Mexico_City" to listOf(
                "Camila Herrera", "Diego Ramírez", "Valentina Cruz", "Mateo Ortega",
                "Renata Flores", "Santiago Morales", "Lucía Vargas", "Emiliano Reyes"
            ),
            "Australia/Sydney" to listOf(
                "Amelia Walker", "Jack Nguyen", "Olivia Singh", "Lachlan Brown",
                "Mia Patel", "Harper Wilson", "Noah Campbell", "Isla Thompson"
            )
        )
        val out = ArrayList<Person>(96)
        var n = 1
        roster.forEach { (zone, names) ->
            names.forEachIndexed { i, name ->
                val morning = i < 4
                out += Person(
                    id = UUID.fromString("00000000-0000-4000-8000-%012d".format(n)),
                    name = name,
                    zone = zone,
                    email = "demo-$n@demo.sangha.local",
                    sitHour = if (morning) 6 else 19,
                    sitMinute = (i % 4) * 8,
                    durationMinutes = 30 + ((n * 37) % 91)
                )
                n += 1
            }
        }
        return out
    }

    private fun halls(): List<HallSeed> = listOf(
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000001"),
            "Kyoto Dawn Zazen",
            "Forty quiet minutes at dawn in Japan.",
            "Asia/Tokyo", "05:30", "DAILY", "", 40, "kyot0530",
            setOf("Asia/Tokyo", "Asia/Seoul")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000002"),
            "Seoul Morning",
            "A weekday morning sit before the city starts.",
            "Asia/Seoul", "06:15", "WEEKDAYS", "", 45, "seou0615",
            setOf("Asia/Seoul")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000003"),
            "London Morning Sit",
            "Weekday silence at seven, London time.",
            "Europe/London", "07:00", "WEEKDAYS", "", 45, "lond0700",
            setOf("Europe/London", "Europe/Berlin")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000004"),
            "Sao Paulo Noon",
            "A short midday sit in Brazil.",
            "America/Sao_Paulo", "12:15", "DAILY", "", 30, "saop1215",
            setOf("America/Sao_Paulo", "America/Mexico_City")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000005"),
            "Nairobi Evening",
            "An hour together as the day cools in Nairobi.",
            "Africa/Nairobi", "18:30", "DAILY", "", 60, "nair1830",
            setOf("Africa/Nairobi")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000006"),
            "New York After Work",
            "A weekday sit after the workday, New York time.",
            "America/New_York", "19:00", "WEEKDAYS", "", 50, "nywk1900",
            setOf("America/New_York", "America/Los_Angeles")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000007"),
            "Berlin Abendstille",
            "Evening stillness in Berlin, a little longer than an hour.",
            "Europe/Berlin", "20:00", "DAILY", "", 75, "berl2000",
            setOf("Europe/Berlin")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000008"),
            "Sydney Sunset",
            "Ninety minutes as the day ends in Sydney.",
            "Australia/Sydney", "20:30", "DAILY", "", 90, "sydn2030",
            setOf("Australia/Sydney")
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-000000000009"),
            "Early Morning Vipassana India",
            "A dawn Vipassana sit, India time.",
            "Asia/Kolkata", "05:00", "DAILY", "", 60, "indm0500",
            setOf("Asia/Kolkata"), nitinId
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-00000000000a"),
            "Late Evening Vipassana Sitting",
            "A late Vipassana sit after the day, India time.",
            "Asia/Kolkata", "21:30", "DAILY", "", 60, "inde2130",
            setOf("Asia/Kolkata"), nitinId
        ),
        HallSeed(
            UUID.fromString("00000000-0000-0000-0001-00000000000b"),
            "Group Vipassana Evening",
            "An evening group Vipassana sit, India time.",
            "Asia/Kolkata", "19:00", "DAILY", "", 45, "indg1900",
            setOf("Asia/Kolkata"), nitinId
        )
    )

    private fun tick() {
        val roster = people()
        val hallSeeds = halls()
        val demoIds = roster.map { it.id }.toSet()
        val moment = Instant.now()
        transaction {
            ensureSeed(roster, hallSeeds, moment)
            seedHistory(roster, hallSeeds, moment)
            syncOpenHall(roster, moment)
            syncScheduledHalls(roster, hallSeeds, demoIds, moment)
        }
    }

    private fun ensureSeed(roster: List<Person>, hallSeeds: List<HallSeed>, moment: Instant) {
        val wanted = roster.map { it.id }.toSet()
        val stale = Users.selectAll()
            .filter { row ->
                val email = row[Users.email]
                email != null && email.endsWith("@demo.sangha.local") && row[Users.id] !in wanted && row[Users.id] != hostId
            }
            .map { it[Users.id] }
        stale.forEach { staleId ->
            CommunityPresence.deleteWhere { userId eq staleId }
            Attendance.deleteWhere { userId eq staleId }
            MeditationLogs.deleteWhere { userId eq staleId }
            HallMemberships.deleteWhere { userId eq staleId }
            Users.deleteWhere { id eq staleId }
        }
        hallSeeds.forEach { hall ->
            HallMemberships.deleteWhere {
                (hallId eq hall.id) and (userId eq UUID.fromString("00000000-0000-0000-0000-000000000001"))
            }
        }
        if (Users.selectAll().none { it[Users.id] == hostId }) {
            Users.insert {
                it[id] = hostId
                it[displayName] = "World Hall Host"
                it[email] = "host@demo.sangha.local"
                it[timezone] = "UTC"
                it[status] = "ACTIVE"
                it[createdAt] = moment
                it[updatedAt] = moment
            }
        }
        roster.forEach { person ->
            if (Users.selectAll().none { it[Users.id] == person.id }) {
                Users.insert {
                    it[id] = person.id
                    it[displayName] = person.name
                    it[email] = person.email
                    it[timezone] = person.zone
                    it[status] = "ACTIVE"
                    it[createdAt] = moment
                    it[updatedAt] = moment
                }
            }
        }
        hallSeeds.forEach { hall ->
            if (Halls.selectAll().none { it[Halls.id] == hall.id }) {
                Halls.insert {
                    it[id] = hall.id
                    it[creatorId] = hall.ownerId ?: hostId
                    it[name] = hall.name
                    it[description] = hall.description
                    it[visibility] = "PUBLIC"
                    it[durationSeconds] = hall.durationMinutes * 60
                    it[timezone] = hall.zone
                    it[status] = "ACTIVE"
                    it[shareCode] = hall.shareCode
                    it[audioType] = "BELL"
                    it[createdAt] = moment
                    it[updatedAt] = moment
                }
                HallSchedules.insert {
                    it[id] = uuid()
                    it[hallId] = hall.id
                    it[scheduleType] = hall.type
                    it[startLocalTime] = hall.start
                    it[timezone] = hall.zone
                    it[daysOfWeek] = hall.days
                    it[enabled] = true
                }
            }
            val owner = hall.ownerId
            if (owner != null && Users.selectAll().any { it[Users.id] == owner }) {
                Halls.update({ Halls.id eq hall.id }) {
                    it[creatorId] = owner
                    it[updatedAt] = moment
                }
                val ownerJoined = HallMemberships.selectAll().any {
                    it[HallMemberships.hallId] == hall.id && it[HallMemberships.userId] == owner
                }
                if (!ownerJoined) {
                    HallMemberships.insert {
                        it[hallId] = hall.id
                        it[userId] = owner
                        it[joinedAt] = moment
                        it[notificationEnabled] = true
                        it[displayMode] = "AVATAR"
                    }
                }
            }
            val members = roster.filter { it.zone in hall.memberZones }
            members.forEach { person ->
                val joined = HallMemberships.selectAll().any {
                    it[HallMemberships.hallId] == hall.id && it[HallMemberships.userId] == person.id
                }
                if (!joined) {
                    HallMemberships.insert {
                        it[hallId] = hall.id
                        it[userId] = person.id
                        it[joinedAt] = moment
                        it[notificationEnabled] = false
                        it[displayMode] = "AVATAR"
                    }
                }
            }
        }
    }

    /**
     * Past completed sittings for each seeded hall. Later sits, including demo
     * users who finish a live window, add their own attendance and logs on top.
     */
    private fun seedHistory(roster: List<Person>, hallSeeds: List<HallSeed>, moment: Instant) {
        hallSeeds.forEachIndexed { hallIndex, hall ->
            val zone = ZoneId.of(hall.zone)
            val time = LocalTime.parse(hall.start)
            val today = LocalDate.now(zone)
            val members = roster.filter { it.zone in hall.memberZones }
            val durationSeconds = hall.durationMinutes * 60
            repeat(18) { ago ->
                val day = today.minusDays((ago + 1).toLong())
                if (!historyDay(hall.type, day)) return@repeat
                val start = ZonedDateTime.of(day, time, zone).toInstant()
                val end = start.plusSeconds(durationSeconds.toLong())
                if (!end.isBefore(moment)) return@repeat
                val sessionId = UUID.nameUUIDFromBytes("${hall.id}|${start.epochSecond}".toByteArray())
                val existing = MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
                if (existing == null) {
                    MeditationSessions.insert {
                        it[id] = sessionId
                        it[hallId] = hall.id
                        it[scheduledStart] = start
                        it[actualStart] = start
                        it[MeditationSessions.durationSeconds] = durationSeconds
                        it[status] = "COMPLETED"
                        it[createdAt] = start
                    }
                }
                members.forEachIndexed { memberIndex, person ->
                    val roll = (hallIndex * 13 + memberIndex * 5 + ago * 3) % 10
                    if (roll > 7) return@forEachIndexed
                    val sitSeconds = (durationSeconds * (6 + (roll % 4)) / 10).coerceIn(15 * 60, durationSeconds)
                    val attendanceId = UUID.nameUUIDFromBytes("$sessionId|${person.id}".toByteArray())
                    if (Attendance.selectAll().none { it[Attendance.id] == attendanceId }) {
                        Attendance.insert {
                            it[id] = attendanceId
                            it[Attendance.sessionId] = sessionId
                            it[userId] = person.id
                            it[hallId] = hall.id
                            it[joinedAt] = start.plusSeconds((memberIndex % 5) * 120L)
                            it[leftAt] = start.plusSeconds(sitSeconds.toLong())
                            it[lastSeenAt] = start.plusSeconds(sitSeconds.toLong())
                            it[attendedDurationSeconds] = sitSeconds
                            it[completionStatus] = "COMPLETED"
                        }
                    }
                    val logId = UUID.nameUUIDFromBytes("log|$sessionId|${person.id}".toByteArray())
                    if (MeditationLogs.selectAll().none { it[MeditationLogs.id] == logId }) {
                        MeditationLogs.insert {
                            it[id] = logId
                            it[userId] = person.id
                            it[MeditationLogs.sessionId] = sessionId
                            it[hallId] = hall.id
                            it[hallName] = hall.name
                            it[date] = day.toString()
                            it[MeditationLogs.durationSeconds] = sitSeconds
                            it[completionStatus] = "COMPLETED"
                            it[createdAt] = end
                        }
                    }
                }
            }
        }
    }

    private fun historyDay(type: String, day: LocalDate): Boolean {
        return when (type) {
            "WEEKDAYS" -> day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
            else -> true
        }
    }

    private fun syncOpenHall(roster: List<Person>, moment: Instant) {
        roster.forEach { person ->
            val sitting = inPersonalSit(person, moment)
            val existing = CommunityPresence.selectAll().firstOrNull { it[CommunityPresence.userId] == person.id }
            if (sitting) {
                if (existing == null) {
                    CommunityPresence.insert {
                        it[userId] = person.id
                        it[displayName] = person.name
                        it[joinedAt] = moment
                        it[lastSeenAt] = moment
                    }
                } else {
                    CommunityPresence.update({ CommunityPresence.userId eq person.id }) {
                        it[displayName] = person.name
                        it[lastSeenAt] = moment
                    }
                }
            } else if (existing != null) {
                CommunityPresence.deleteWhere { userId eq person.id }
            }
        }
    }

    private fun inPersonalSit(person: Person, moment: Instant): Boolean {
        val zone = ZoneId.of(person.zone)
        val local = ZonedDateTime.ofInstant(moment, zone)
        val start = ZonedDateTime.of(local.toLocalDate(), LocalTime.of(person.sitHour, person.sitMinute), zone)
        val end = start.plusMinutes(person.durationMinutes.toLong())
        return !local.isBefore(start) && local.isBefore(end)
    }

    private fun syncScheduledHalls(
        roster: List<Person>,
        hallSeeds: List<HallSeed>,
        demoIds: Set<UUID>,
        moment: Instant
    ) {
        val byId = roster.associateBy { it.id }
        hallSeeds.forEach { seed ->
            val hall = Halls.selectAll().firstOrNull { it[Halls.id] == seed.id } ?: return@forEach
            val schedule = HallSchedules.selectAll().firstOrNull { it[HallSchedules.hallId] == seed.id } ?: return@forEach
            val spec = ScheduleSpec(
                type = schedule[HallSchedules.scheduleType],
                startLocalTime = schedule[HallSchedules.startLocalTime],
                timezone = schedule[HallSchedules.timezone],
                daysOfWeek = schedule[HallSchedules.daysOfWeek],
                startDate = schedule[HallSchedules.startDate],
                durationSeconds = hall[Halls.durationSeconds]
            )
            val startMillis = ScheduleMath.nextStartMillis(spec, moment) ?: return@forEach
            val sessionOpen = ScheduleMath.remainingSeconds(startMillis, hall[Halls.durationSeconds], moment.toEpochMilli()) != null
            val start = Instant.ofEpochMilli(startMillis)
            val sessionId = if (sessionOpen) ensureSession(seed.id, start, hall[Halls.durationSeconds], moment) else null
            val members = HallMemberships.selectAll()
                .filter { it[HallMemberships.hallId] == seed.id && it[HallMemberships.userId] in demoIds }
                .map { it[HallMemberships.userId] }
            members.forEach { userId ->
                val person = byId[userId] ?: return@forEach
                val shouldSit = sessionOpen && sessionId != null && inHallSlice(person, start, hall[Halls.durationSeconds], moment)
                val open = Attendance.selectAll().firstOrNull {
                    it[Attendance.userId] == userId &&
                        it[Attendance.hallId] == seed.id &&
                        it[Attendance.leftAt] == null
                }
                if (shouldSit) {
                    if (open == null || open[Attendance.sessionId] != sessionId) {
                        if (open != null) closeAttendance(open[Attendance.id], person, moment)
                        Attendance.insert {
                            it[id] = uuid()
                            it[Attendance.sessionId] = sessionId!!
                            it[Attendance.userId] = userId
                            it[hallId] = seed.id
                            it[joinedAt] = moment
                            it[lastSeenAt] = moment
                            it[attendedDurationSeconds] = 0
                            it[completionStatus] = "UNKNOWN"
                        }
                    } else {
                        Attendance.update({ Attendance.id eq open[Attendance.id] }) {
                            it[lastSeenAt] = moment
                        }
                    }
                } else if (open != null) {
                    closeAttendance(open[Attendance.id], person, moment)
                }
            }
        }
    }

    private fun inHallSlice(person: Person, hallStart: Instant, hallDurationSeconds: Int, moment: Instant): Boolean {
        val offsetSeconds = ((person.id.leastSignificantBits and 0x7fffffff) % 5) * 3 * 60
        val sitSeconds = minOf(person.durationMinutes * 60L, hallDurationSeconds - offsetSeconds)
        if (sitSeconds <= 0) return false
        val from = hallStart.plusSeconds(offsetSeconds)
        val until = from.plusSeconds(sitSeconds)
        return !moment.isBefore(from) && moment.isBefore(until)
    }

    private fun ensureSession(hallId: UUID, start: Instant, durationSeconds: Int, moment: Instant): UUID {
        val existing = MeditationSessions.selectAll().firstOrNull {
            it[MeditationSessions.hallId] == hallId && it[MeditationSessions.scheduledStart] == start
        }
        if (existing != null) {
            if (existing[MeditationSessions.status] == "SCHEDULED") {
                MeditationSessions.update({ MeditationSessions.id eq existing[MeditationSessions.id] }) {
                    it[status] = "ACTIVE"
                    it[actualStart] = start
                }
            }
            return existing[MeditationSessions.id]
        }
        val id = uuid()
        MeditationSessions.insert {
            it[MeditationSessions.id] = id
            it[MeditationSessions.hallId] = hallId
            it[scheduledStart] = start
            it[actualStart] = start
            it[MeditationSessions.durationSeconds] = durationSeconds
            it[status] = "ACTIVE"
            it[createdAt] = moment
        }
        return id
    }

    private fun closeAttendance(attendanceId: UUID, person: Person, moment: Instant) {
        val row = Attendance.selectAll().first { it[Attendance.id] == attendanceId }
        val joined = row[Attendance.joinedAt]
        val seconds = java.time.Duration.between(joined, moment).seconds.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        Attendance.update({ Attendance.id eq attendanceId }) {
            it[leftAt] = moment
            it[lastSeenAt] = moment
            it[attendedDurationSeconds] = seconds
            it[completionStatus] = "COMPLETED"
        }
        val sessionId = row[Attendance.sessionId]
        val already = MeditationLogs.selectAll().any {
            it[MeditationLogs.userId] == person.id && it[MeditationLogs.sessionId] == sessionId
        }
        if (!already && seconds > 0) {
            val hallName = Halls.selectAll().firstOrNull { it[Halls.id] == row[Attendance.hallId] }?.get(Halls.name) ?: "Hall"
            val date = ZonedDateTime.ofInstant(moment, ZoneId.of(person.zone)).toLocalDate().toString()
            MeditationLogs.insert {
                it[id] = uuid()
                it[userId] = person.id
                it[MeditationLogs.sessionId] = sessionId
                it[hallId] = row[Attendance.hallId]
                it[MeditationLogs.hallName] = hallName
                it[MeditationLogs.date] = date
                it[durationSeconds] = seconds
                it[completionStatus] = "COMPLETED"
                it[createdAt] = now()
            }
        }
    }
}
