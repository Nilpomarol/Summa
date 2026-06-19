package com.gestorfinances.app.golden

import com.gestorfinances.app.domain.rules.AutoCategorizeAction
import com.gestorfinances.app.domain.rules.AutoCategorizeConditions
import com.gestorfinances.app.domain.rules.AutoCategorizeMovement
import com.gestorfinances.app.domain.rules.AutoCategorizeRule
import com.gestorfinances.app.domain.rules.AutoCategorizer
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DuplicateDetector
import com.gestorfinances.app.domain.rules.DuplicateMovement
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurrenceRule
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.SplitCalculator
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenVectorTest {
    private val repoRoot = File(requireNotNull(System.getProperty("gestor.repo.root")))
    private val goldenRoot = repoRoot.resolve("shared/golden")
    private val sharedRoot = repoRoot.resolve("shared")
    private val json = Json

    @Test
    fun allGoldenFilesAreCovered() {
        val covered = setOf(
            "account_flow.json",
            "auto_categorize.json",
            "debt_balance.json",
            "duplicate_detection.json",
            "recurring_advance.json",
            "refund_actual.json",
            "split_rounding.json",
        )

        assertEquals(covered, goldenRoot.listFiles { file -> file.extension == "json" }!!.map { it.name }.toSet())
    }

    @Test
    fun splitRoundingMatchesGoldenVectors() {
        golden("split_rounding.json").cases().forEach { case ->
            val input = case.obj("input")
            val expected = case.obj("expected")
            val result = when (input.string("method")) {
                "equal" -> SplitCalculator.equal(
                    totalCents = input.long("total_cents"),
                    participantCount = input.int("participant_count"),
                    payerIndex = input.int("payer_index"),
                )
                "percentage" -> SplitCalculator.percentage(
                    totalCents = input.long("total_cents"),
                    basisPoints = input.intArray("bps"),
                    payerIndex = input.int("payer_index"),
                )
                "exact" -> SplitCalculator.exact(
                    totalCents = input.long("total_cents"),
                    amountsCents = input.longArray("amounts_cents"),
                )
                else -> error("Unknown split method in ${case.name()}")
            }

            expected.optionalBoolean("valid")?.let {
                assertEquals(case.name(), it, result.valid)
            }
            expected.optionalLongArray("shares_cents")?.let {
                assertEquals(case.name(), it, result.sharesCents)
            }
            expected.optionalString("reason")?.let {
                assertEquals(case.name(), it, result.reason)
            }
        }
    }

    @Test
    fun recurringAdvanceMatchesGoldenVectors() {
        golden("recurring_advance.json").cases().forEach { case ->
            val input = case.obj("input")
            val result = RecurringAdvancer.advance(
                rule = RecurrenceRule(
                    frequency = input.recurrenceFrequency(),
                    dayOfMonth = input.optionalInt("day_of_month"),
                    intervalCount = input.optionalLong("interval_count"),
                    customUnit = input.optionalCustomUnit("custom_unit"),
                ),
                cursor = LocalDate.parse(input.string("cursor")),
                today = LocalDate.parse(input.string("today")),
            )

            val expected = case.obj("expected")
            assertEquals(case.name(), expected.stringArray("due"), result.dueDates.map { it.toString() })
            assertEquals(case.name(), expected.string("new_cursor"), result.newCursor.toString())
        }
    }

    @Test
    fun autoCategorizeMatchesGoldenVectors() {
        golden("auto_categorize.json").cases().forEach { case ->
            val input = case.obj("input")
            val match = AutoCategorizer.findMatch(
                movement = input.obj("movement").toAutoCategorizeMovement(),
                rules = input.array("rules").map { it.jsonObject.toAutoCategorizeRule() },
            )

            val expected = case.obj("expected")
            assertEquals(case.name(), expected.optionalString("winning_rule_id"), match?.ruleId)
            assertEquals(case.name(), expected.optionalAction("action"), match?.action)
        }
    }

    @Test
    fun duplicateDetectionMatchesGoldenVectors() {
        golden("duplicate_detection.json").cases().forEach { case ->
            val input = case.obj("input")
            val result = DuplicateDetector.isDuplicate(
                existing = input.obj("existing").toDuplicateMovement(),
                candidate = input.obj("candidate").toDuplicateMovement(),
            )

            assertEquals(case.name(), case.obj("expected").boolean("is_duplicate"), result)
        }
    }

    @Test
    fun accountFlowMatchesGoldenVectors() {
        golden("account_flow.json").cases().forEach { case ->
            freshConnection().use { connection ->
                val input = case.obj("input")
                input.array("accounts").forEach { connection.insertAccount(it.jsonObject) }
                connection.insertPeopleReferencedBy(input.array("movements"))
                input.array("movements").forEach { connection.insertMovement(it.jsonObject) }

                val expected = case.obj("expected")
                assertEquals(
                    case.name(),
                    expected.obj("current_balance_cents").longMap(),
                    connection.longMap("SELECT account_id, current_balance_cents FROM v_account_balance"),
                )
                assertEquals(
                    case.name(),
                    expected.long("net_worth_cents"),
                    connection.singleLong("SELECT SUM(current_balance_cents) FROM v_account_balance"),
                )
            }
        }
    }

    @Test
    fun debtBalanceMatchesGoldenVectors() {
        golden("debt_balance.json").cases().forEach { case ->
            freshConnection().use { connection ->
                val input = case.obj("input")
                input.array("people").forEach { connection.insertPerson(it.jsonObject.string("id")) }
                connection.insertAccountsReferencedBy(input.array("movements"))
                input.array("movements").forEach { connection.insertMovement(it.jsonObject) }
                input.array("splits").forEach { connection.insertSplit(it.jsonObject) }
                input.array("split_lines").forEachIndexed { index, row ->
                    connection.insertSplitLine("sl-$index", row.jsonObject)
                }

                assertEquals(
                    case.name(),
                    case.obj("expected").obj("balance_cents").longMap(),
                    connection.longMap("SELECT person_id, balance_cents FROM v_person_balance"),
                )
            }
        }
    }

    @Test
    fun refundActualMatchesGoldenVectors() {
        golden("refund_actual.json").cases().forEach { case ->
            freshConnection().use { connection ->
                val input = case.obj("input")
                connection.insertAccount(id = "acc-1", startingBalanceCents = 0)
                connection.insertMovement(
                    id = "expense-1",
                    type = "expense",
                    accountId = "acc-1",
                    amountCents = 20_000,
                    date = "2026-03-01",
                )
                connection.insertMovement(
                    id = "refund-1",
                    type = "refund",
                    accountId = "acc-1",
                    amountCents = input.long("amount_cents"),
                    date = "2026-03-02",
                    refundsExpenseId = "expense-1",
                    actualRefundCents = input.optionalLong("actual_refund_cents"),
                )

                val expected = case.obj("expected")
                assertEquals(
                    case.name(),
                    expected.long("flow_delta_cents"),
                    connection.singleLong(
                        "SELECT delta_cents FROM v_account_flow WHERE movement_id = 'refund-1'",
                    ),
                )
                assertEquals(
                    case.name(),
                    -expected.long("actual_reduction_cents"),
                    connection.singleLong(
                        "SELECT amount_cents FROM v_actual_expense WHERE source_id = 'refund-1'",
                    ),
                )
            }
        }
    }

    private fun golden(fileName: String): JsonObject =
        json.parseToJsonElement(goldenRoot.resolve(fileName).readText()).jsonObject

    private fun freshConnection(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { connection ->
            connection.createStatement().use { statement ->
                sharedRoot.resolve("migrations/001_initial.sql").readSqlStatements().forEach(statement::execute)
                listOf(
                    "v_movement_shared.sql",
                    "v_account_flow.sql",
                    "v_account_balance.sql",
                    "v_actual_expense.sql",
                    "v_actual_income.sql",
                    "v_person_balance.sql",
                ).forEach { fileName ->
                    sharedRoot.resolve("queries/$fileName").readSqlStatements().forEach(statement::execute)
                }
            }
        }

    private fun File.readSqlStatements(): List<String> =
        readText()
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun Connection.insertAccountsReferencedBy(rows: JsonArray) {
        rows.mapNotNull { it.jsonObject.optionalString("account_id") }.forEach { insertAccount(it, 0) }
        rows.mapNotNull { it.jsonObject.optionalString("dest_account_id") }.forEach { insertAccount(it, 0) }
    }

    private fun Connection.insertPeopleReferencedBy(rows: JsonArray) {
        rows.mapNotNull { it.jsonObject.optionalString("person_id") }.forEach { insertPerson(it) }
    }

    private fun Connection.insertAccount(row: JsonObject) {
        insertAccount(
            id = row.string("id"),
            startingBalanceCents = row.optionalLong("starting_balance_cents") ?: 0,
        )
    }

    private fun Connection.insertAccount(
        id: String,
        startingBalanceCents: Long,
    ) {
        prepareStatement(
            """
            INSERT OR IGNORE INTO accounts(
                id, name, starting_balance_cents, type, created_at, updated_at
            ) VALUES (?, ?, ?, 'bank', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, id)
            statement.setLong(3, startingBalanceCents)
            statement.setString(4, NOW)
            statement.setString(5, NOW)
            statement.executeUpdate()
        }
    }

    private fun Connection.insertPerson(id: String) {
        prepareStatement(
            "INSERT OR IGNORE INTO people(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, id)
            statement.setString(3, NOW)
            statement.setString(4, NOW)
            statement.executeUpdate()
        }
    }

    private fun Connection.insertMovement(row: JsonObject) {
        insertMovement(
            id = row.string("id"),
            type = row.string("type"),
            accountId = row.string("account_id"),
            amountCents = row.long("amount_cents"),
            date = row.string("date"),
            destAccountId = row.optionalString("dest_account_id"),
            personId = row.optionalString("person_id"),
            settlementDirection = row.optionalString("settlement_direction"),
            refundsExpenseId = row.optionalString("refunds_expense_id"),
            actualRefundCents = row.optionalLong("actual_refund_cents"),
        )
    }

    private fun Connection.insertMovement(
        id: String,
        type: String,
        accountId: String,
        amountCents: Long,
        date: String,
        destAccountId: String? = null,
        personId: String? = null,
        settlementDirection: String? = null,
        refundsExpenseId: String? = null,
        actualRefundCents: Long? = null,
    ) {
        prepareStatement(
            """
            INSERT INTO movements(
                id, type, account_id, dest_account_id, amount_cents, date,
                person_id, settlement_direction, refunds_expense_id, actual_refund_cents,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, type)
            statement.setString(3, accountId)
            statement.setNullableString(4, destAccountId)
            statement.setLong(5, amountCents)
            statement.setString(6, date)
            statement.setNullableString(7, personId)
            statement.setNullableString(8, settlementDirection)
            statement.setNullableString(9, refundsExpenseId)
            statement.setNullableLong(10, actualRefundCents)
            statement.setString(11, NOW)
            statement.setString(12, NOW)
            statement.executeUpdate()
        }
    }

    private fun Connection.insertSplit(row: JsonObject) {
        prepareStatement(
            """
            INSERT INTO splits(
                id, movement_id, payer_person_id, entry_method,
                total_amount_cents, date, created_at, updated_at
            ) VALUES (?, ?, ?, 'equal', ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, row.string("id"))
            statement.setNullableString(2, row.optionalString("movement_id"))
            statement.setNullableString(3, row.optionalString("payer_person_id"))
            statement.setNullableLong(4, row.optionalLong("total_amount_cents"))
            statement.setNullableString(5, row.optionalString("date"))
            statement.setString(6, NOW)
            statement.setString(7, NOW)
            statement.executeUpdate()
        }
    }

    private fun Connection.insertSplitLine(
        id: String,
        row: JsonObject,
    ) {
        prepareStatement(
            """
            INSERT INTO split_lines(
                id, split_id, participant_kind, person_id,
                owed_amount_cents, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, row.string("split_id"))
            statement.setString(3, row.string("participant_kind"))
            statement.setNullableString(4, row.optionalString("person_id"))
            statement.setLong(5, row.long("owed_amount_cents"))
            statement.setString(6, NOW)
            statement.setString(7, NOW)
            statement.executeUpdate()
        }
    }

    private fun Connection.longMap(sql: String): Map<String, Long> {
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                val values = linkedMapOf<String, Long>()
                while (result.next()) {
                    values[result.getString(1)] = result.getLong(2)
                }
                return values
            }
        }
    }

    private fun Connection.singleLong(sql: String): Long {
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                assertTrue(result.next())
                return result.getLong(1)
            }
        }
    }

    private fun JsonObject.cases(): List<JsonObject> = array("cases").map { it.jsonObject }
    private fun JsonObject.name(): String = string("name")
    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
    private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray
    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
    private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
    private fun JsonObject.boolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

    private fun JsonObject.optionalString(key: String): String? =
        get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private fun JsonObject.optionalInt(key: String): Int? =
        get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.int

    private fun JsonObject.optionalLong(key: String): Long? =
        get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.long

    private fun JsonObject.optionalBoolean(key: String): Boolean? =
        get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.boolean

    private fun JsonObject.intArray(key: String): List<Int> =
        array(key).map { it.jsonPrimitive.int }

    private fun JsonObject.longArray(key: String): List<Long> =
        array(key).map { it.jsonPrimitive.long }

    private fun JsonObject.stringArray(key: String): List<String> =
        array(key).map { it.jsonPrimitive.content }

    private fun JsonObject.optionalLongArray(key: String): List<Long>? =
        get(key)?.takeUnless { it is JsonNull }?.jsonArray?.map { it.jsonPrimitive.long }

    private fun JsonObject.longMap(): Map<String, Long> =
        entries.associate { (key, value) -> key to value.jsonPrimitive.long }

    private fun JsonObject.recurrenceFrequency(): RecurrenceFrequency =
        when (string("frequency")) {
            "weekly" -> RecurrenceFrequency.WEEKLY
            "fortnightly" -> RecurrenceFrequency.FORTNIGHTLY
            "monthly" -> RecurrenceFrequency.MONTHLY
            "yearly" -> RecurrenceFrequency.YEARLY
            "custom" -> RecurrenceFrequency.CUSTOM
            else -> error("Unknown frequency ${string("frequency")}")
        }

    private fun JsonObject.optionalCustomUnit(key: String): CustomRecurrenceUnit? =
        optionalString(key)?.let {
            when (it) {
                "days" -> CustomRecurrenceUnit.DAYS
                "weeks" -> CustomRecurrenceUnit.WEEKS
                "months" -> CustomRecurrenceUnit.MONTHS
                "years" -> CustomRecurrenceUnit.YEARS
                else -> error("Unknown custom unit $it")
            }
        }

    private fun JsonObject.toAutoCategorizeMovement(): AutoCategorizeMovement =
        AutoCategorizeMovement(
            name = optionalString("name"),
            payee = optionalString("payee"),
            amountCents = long("amount_cents"),
            date = LocalDate.parse(string("date")),
            accountId = string("account_id"),
        )

    private fun JsonObject.toAutoCategorizeRule(): AutoCategorizeRule =
        AutoCategorizeRule(
            id = string("id"),
            priority = int("priority"),
            createdAt = string("created_at"),
            active = int("active") == 1,
            conditions = obj("conditions").toAutoCategorizeConditions(),
            action = obj("action").toAutoCategorizeAction(),
        )

    private fun JsonObject.toAutoCategorizeConditions(): AutoCategorizeConditions =
        AutoCategorizeConditions(
            textContains = optionalString("text_contains"),
            amountMinCents = optionalLong("amount_min_cents"),
            amountMaxCents = optionalLong("amount_max_cents"),
            accountId = optionalString("account_id"),
            dayOfMonthIn = get("day_of_month_in")
                ?.takeUnless { it is JsonNull }
                ?.jsonArray
                ?.map { it.jsonPrimitive.int }
                ?.toSet()
                ?: emptySet(),
        )

    private fun JsonObject.toAutoCategorizeAction(): AutoCategorizeAction =
        AutoCategorizeAction(
            categoryId = optionalString("set_category_id"),
            tripId = optionalString("set_trip_id"),
        )

    private fun JsonObject.optionalAction(key: String): AutoCategorizeAction? {
        val value: JsonElement = get(key) ?: return null
        if (value is JsonNull) return null
        return value.jsonObject.toAutoCategorizeAction()
    }

    private fun JsonObject.toDuplicateMovement(): DuplicateMovement =
        DuplicateMovement(
            accountId = string("account_id"),
            amountCents = long("amount_cents"),
            date = LocalDate.parse(string("date")),
            name = string("name"),
        )

    private fun java.sql.PreparedStatement.setNullableString(
        parameterIndex: Int,
        value: String?,
    ) {
        if (value == null) {
            setNull(parameterIndex, java.sql.Types.VARCHAR)
        } else {
            setString(parameterIndex, value)
        }
    }

    private fun java.sql.PreparedStatement.setNullableLong(
        parameterIndex: Int,
        value: Long?,
    ) {
        if (value == null) {
            setNull(parameterIndex, java.sql.Types.INTEGER)
        } else {
            setLong(parameterIndex, value)
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
