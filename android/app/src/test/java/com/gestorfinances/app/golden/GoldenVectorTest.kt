package com.gestorfinances.app.golden

import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DebtConsumption
import com.gestorfinances.app.domain.rules.DebtItem
import com.gestorfinances.app.domain.rules.DuplicateDetector
import com.gestorfinances.app.domain.rules.DuplicateMovement
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.domain.rules.GoalProgress
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurrenceRule
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.SettlementScope
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
            "debt_balance.json",
            "debt_consumption.json",
            "duplicate_detection.json",
            "goal_progress.json",
            "recurring_advance.json",
            "refund_actual.json",
            "shared_account.json",
            "split_rounding.json",
            "template_split_rescale.json",
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
    fun templateSplitRescaleMatchesGoldenVectors() {
        golden("template_split_rescale.json").cases().forEach { case ->
            val input = case.obj("input")
            val result = SplitCalculator.rescale(
                weightsCents = input.longArray("weights_cents"),
                totalCents = input.long("total_cents"),
                payerIndex = input.int("payer_index"),
            )

            val expected = case.obj("expected")
            assertEquals(case.name(), expected.longArray("shares_cents"), result.sharesCents)
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
    fun sharedAccountsMatchGoldenVectors() {
        golden("shared_account.json").cases().forEach { case ->
            freshConnection().use { connection ->
                val input = case.obj("input")
                connection.createStatement().use { statement ->
                    val sql = """
                        INSERT INTO accounts (id,name,starting_balance_cents,type,ownership_kind,created_at,updated_at) VALUES
                          ('personal','Personal',${input.long("personal_start_cents")},'bank','personal','$NOW','$NOW'),
                          ('shared','Shared',${input.long("shared_start_cents")},'bank','personal','$NOW','$NOW');
                        INSERT INTO people (id,name,created_at,updated_at) VALUES ('person','Person','$NOW','$NOW');
                        INSERT INTO account_members (id,account_id,participant_kind,person_id,ownership_basis_points,default_expense_basis_points,created_at,updated_at) VALUES
                          ('member-user','shared','user',NULL,${input.long("owner_ownership_basis_points")},4000,'$NOW','$NOW'),
                          ('member-person','shared','person','person',${10_000 - input.long("owner_ownership_basis_points")},6000,'$NOW','$NOW');
                        UPDATE accounts SET ownership_kind = 'shared' WHERE id = 'shared';
                        INSERT INTO account_contributions (id,shared_account_id,direction,contributor_kind,person_id,source_account_id,amount_cents,date,created_at,updated_at) VALUES
                          ('owner-contribution','shared','in','user',NULL,'personal',${input.long("owner_contribution_cents")},'2026-01-02','$NOW','$NOW'),
                          ('person-contribution','shared','in','person','person',NULL,${input.long("person_contribution_cents")},'2026-01-03','$NOW','$NOW');
                        INSERT INTO movements (id,type,amount_cents,date,account_id,expense_funding,shared_split_id,created_at,updated_at) VALUES
                          ('shared-expense','expense',${input.long("shared_expense_cents")},'2026-01-04','shared','shared_account','shared-split','$NOW','$NOW'),
                          ('owner-expense','expense',${input.long("owner_financed_expense_cents")},'2026-01-05','personal','owner',NULL,'$NOW','$NOW');
                        INSERT INTO splits (id,movement_id,entry_method,created_at,updated_at) VALUES
                          ('shared-split','shared-expense','exact','$NOW','$NOW'),
                          ('owner-split','owner-expense','exact','$NOW','$NOW');
                        INSERT INTO split_lines (id,split_id,participant_kind,person_id,owed_amount_cents,created_at,updated_at) VALUES
                          ('shared-user','shared-split','user',NULL,${input.long("shared_expense_owner_share_cents")},'$NOW','$NOW'),
                          ('shared-person','shared-split','person','person',${input.long("shared_expense_cents") - input.long("shared_expense_owner_share_cents")},'$NOW','$NOW'),
                          ('owner-user','owner-split','user',NULL,${input.long("owner_financed_expense_cents") - input.long("owner_financed_person_share_cents")},'$NOW','$NOW'),
                          ('owner-person','owner-split','person','person',${input.long("owner_financed_person_share_cents")},'$NOW','$NOW');
                        """.trimIndent() +
                        // A member takes money out the same way it went in, and an income into the
                        // shared account carries the allocation that says whose income it is.
                        withdrawal("owner-withdrawal", "user", "NULL", "'personal'", input.optionalLong("owner_withdrawal_cents"), "2026-01-06") +
                        withdrawal("person-withdrawal", "person", "'person'", "NULL", input.optionalLong("person_withdrawal_cents"), "2026-01-07") +
                        sharedIncome(input.optionalLong("shared_income_cents"), input.optionalLong("shared_income_owner_share_cents"))
                    // A shared-account expense names the split it is consumed through, which is
                    // written after it: the deferred foreign key only resolves at commit, so the
                    // fixture goes in as one transaction, the way the app writes it.
                    connection.autoCommit = false
                    sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach(statement::execute)
                    connection.commit()
                    connection.autoCommit = true
                }
                val expected = case.obj("expected")
                assertEquals(case.name(), expected.long("personal_physical_cents"), connection.singleLong("SELECT current_balance_cents FROM v_account_balance WHERE account_id='personal'"))
                assertEquals(case.name(), expected.long("shared_physical_cents"), connection.singleLong("SELECT current_balance_cents FROM v_account_balance WHERE account_id='shared'"))
                assertEquals(case.name(), expected.long("shared_owner_value_cents"), connection.singleLong("SELECT owner_value_cents FROM v_account_value WHERE account_id='shared'"))
                assertEquals(case.name(), expected.long("net_worth_cents"), connection.singleLong("SELECT SUM(owner_value_cents) FROM v_account_value"))
                assertEquals(case.name(), expected.long("actual_expense_cents"), connection.singleLong("SELECT COALESCE(SUM(amount_cents),0) FROM v_actual_expense"))
                assertEquals(case.name(), expected.long("actual_income_cents"), connection.singleLong("SELECT COALESCE(SUM(amount_cents),0) FROM v_actual_income"))
                assertEquals(case.name(), expected.long("person_balance_cents"), connection.singleLong("SELECT balance_cents FROM v_person_balance WHERE person_id='person'"))
            }
        }
    }

    @Test
    fun debtConsumptionMatchesGoldenVectors() {
        golden("debt_consumption.json").cases().forEach { case ->
            val items = case.obj("input").array("items").map { it.jsonObject.toDebtItem() }
            val projection = DebtConsumption.project(items)
            val expected = case.obj("expected")

            assertEquals(case.name(), expected.long("balance_cents"), projection.totalCents)
            assertEquals(
                case.name(),
                expected.array("residuals").map { row ->
                    val obj = row.jsonObject
                    Triple(obj.string("source_id"), obj.long("original_cents"), obj.long("remaining_cents"))
                },
                projection.residuals.map { Triple(it.sourceId, it.originalCents, it.remainingCents) },
            )
            assertEquals(case.name(), expected.long("credit_all_cents"), projection.creditAllCents)
            assertEquals(case.name(), expected.long("credit_recurring_cents"), projection.creditRecurringCents)

            // The projection only ever explains the balance; v_person_balance owns the total.
            assertEquals(
                "${case.name()} (residuals must reconcile with the balance)",
                projection.totalCents,
                projection.residuals.sumOf { it.remainingCents } +
                    projection.creditAllCents +
                    projection.creditRecurringCents,
            )
        }
    }

    @Test
    fun goalProgressMatchesGoldenVectors() {
        golden("goal_progress.json").cases().forEach { case ->
            val input = case.obj("input")
            val expected = case.obj("expected")

            val progress = GoalProgress.evaluate(
                fundingMode = GoalFundingMode.fromDb(input.string("funding_mode")),
                targetAmountCents = input.long("target_amount_cents"),
                accountBalanceCents = input.long("account_balance_cents"),
                allocationCents = input.longArray("allocation_cents"),
                targetDate = input.optionalString("target_date")?.let(LocalDate::parse),
                today = LocalDate.parse(input.string("today")),
            )

            assertEquals(case.name(), expected.long("saved_cents"), progress.savedCents)
            assertEquals(case.name(), expected.long("remaining_cents"), progress.remainingCents)
            assertEquals(case.name(), expected.boolean("reached"), progress.reached)
            assertEquals(case.name(), expected.optionalInt("months_remaining"), progress.monthsRemaining)
            assertEquals(case.name(), expected.optionalLong("monthly_pace_cents"), progress.monthlyPaceCents)
            assertEquals(case.name(), expected.boolean("overdue"), progress.overdue)
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
                sharedRoot.resolve("schema/schema.sql").readSqlStatements().forEach(statement::execute)
                listOf(
                    "v_movement_shared.sql",
                    "v_account_flow.sql",
                    "v_account_balance.sql",
                    "v_account_value.sql",
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
        rows.flatMap { row -> listOf("person_id", "payer_person_id").mapNotNull { row.jsonObject.optionalString(it) } }
            .forEach { insertPerson(it) }
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
            accountId = row.optionalString("account_id"),
            amountCents = row.long("amount_cents"),
            date = row.string("date"),
            destAccountId = row.optionalString("dest_account_id"),
            personId = row.optionalString("person_id"),
            settlementDirection = row.optionalString("settlement_direction"),
            refundsExpenseId = row.optionalString("refunds_expense_id"),
            actualRefundCents = row.optionalLong("actual_refund_cents"),
            archivedAt = row.optionalString("archived_at"),
            payerPersonId = row.optionalString("payer_person_id"),
        )
    }

    private fun Connection.insertMovement(
        id: String,
        type: String,
        accountId: String?,
        amountCents: Long,
        date: String,
        destAccountId: String? = null,
        personId: String? = null,
        settlementDirection: String? = null,
        refundsExpenseId: String? = null,
        actualRefundCents: Long? = null,
        archivedAt: String? = null,
        payerPersonId: String? = null,
    ) {
        prepareStatement(
            """
            INSERT INTO movements(
                id, type, account_id, dest_account_id, amount_cents, date,
                person_id, settlement_direction, refunds_expense_id, actual_refund_cents,
                settlement_scope, expense_funding, payer_person_id, created_at, updated_at, archived_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, type)
            statement.setNullableString(3, accountId)
            statement.setNullableString(4, destAccountId)
            statement.setLong(5, amountCents)
            statement.setString(6, date)
            statement.setNullableString(7, personId)
            statement.setNullableString(8, settlementDirection)
            statement.setNullableString(9, refundsExpenseId)
            statement.setNullableLong(10, actualRefundCents)
            statement.setNullableString(11, if (type == "settlement") "all" else null)
            statement.setNullableString(12, if (type == "expense" && payerPersonId == null) "owner" else null)
            statement.setNullableString(13, payerPersonId)
            statement.setString(14, NOW)
            statement.setString(15, NOW)
            statement.setNullableString(16, archivedAt)
            statement.executeUpdate()
        }
    }

    private fun Connection.insertSplit(row: JsonObject) {
        prepareStatement(
            """
            INSERT INTO splits(id, movement_id, entry_method, created_at, updated_at, archived_at)
            VALUES (?, ?, 'equal', ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, row.string("id"))
            statement.setString(2, row.string("movement_id"))
            statement.setString(3, NOW)
            statement.setString(4, NOW)
            statement.setNullableString(5, row.optionalString("archived_at"))
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
                owed_amount_cents, created_at, updated_at, archived_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, row.string("split_id"))
            statement.setString(3, row.string("participant_kind"))
            statement.setNullableString(4, row.optionalString("person_id"))
            statement.setLong(5, row.long("owed_amount_cents"))
            statement.setString(6, NOW)
            statement.setString(7, NOW)
            statement.setNullableString(8, row.optionalString("archived_at"))
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
    /** One member money-out row, or nothing when the case does not exercise a withdrawal. */
    private fun withdrawal(
        id: String,
        contributorKind: String,
        personId: String,
        ownerAccountId: String,
        amountCents: Long?,
        date: String,
    ): String = if (amountCents == null) "" else """

        INSERT INTO account_contributions (id,shared_account_id,direction,contributor_kind,person_id,source_account_id,amount_cents,date,created_at,updated_at)
        VALUES ('$id','shared','out','$contributorKind',$personId,$ownerAccountId,$amountCents,'$date','$NOW','$NOW');
    """.trimIndent()

    /** An income into the shared account carrying the allocation that says whose income it is. */
    private fun sharedIncome(amountCents: Long?, ownerShareCents: Long?): String =
        if (amountCents == null) "" else """

            INSERT INTO movements (id,type,amount_cents,date,account_id,created_at,updated_at)
            VALUES ('shared-income','income',$amountCents,'2026-01-08','shared','$NOW','$NOW');
            INSERT INTO splits (id,movement_id,entry_method,created_at,updated_at)
            VALUES ('income-split','shared-income','exact','$NOW','$NOW');
            INSERT INTO split_lines (id,split_id,participant_kind,person_id,owed_amount_cents,created_at,updated_at) VALUES
              ('income-user','income-split','user',NULL,${ownerShareCents ?: 0},'$NOW','$NOW'),
              ('income-person','income-split','person','person',${amountCents - (ownerShareCents ?: 0)},'$NOW','$NOW');
        """.trimIndent()

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

    private fun JsonObject.toDebtItem(): DebtItem {
        val type = string("type")
        return DebtItem(
            sourceId = string("source_id"),
            date = string("date"),
            effectCents = long("effect_cents"),
            isSettlement = type.startsWith("settlement"),
            isRecurring = optionalBoolean("is_recurring") ?: false,
            scope = optionalString("scope")?.let(SettlementScope::fromDb),
        )
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
