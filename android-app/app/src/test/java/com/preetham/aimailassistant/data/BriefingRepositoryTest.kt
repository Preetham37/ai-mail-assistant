package com.preetham.aimailassistant.data

import com.google.gson.Gson
import com.preetham.aimailassistant.security.InMemoryAccountStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BriefingRepository, verifying:
 * 1. enrichAccounts() account merging logic with local Knox storage retention
 * 2. filterByAccount() filtering across account IDs, emails, and legacy primary/default aliases
 * 3. Gson serialization and deserialization of BriefingData and AccountMeta
 */
class BriefingRepositoryTest {

    private lateinit var storage: InMemoryAccountStorage
    private lateinit var repository: BriefingRepository
    private val gson = Gson()

    @Before
    fun setUp() {
        storage = InMemoryAccountStorage()
        repository = BriefingRepository(context = null, secureAccountStorage = storage)
    }

    // =========================================================================
    // 1. enrichAccounts() Tests
    // =========================================================================

    @Test
    fun `enrichAccounts merges incoming remote accounts with local storage without dropping local accounts`() {
        // Setup existing account in Knox storage
        val localAccount = AccountMeta(
            id = "acc_local_personal",
            email = "local@briefingos.com",
            display_name = "Local User",
            label = "PERSONAL",
            color_hex = "#8B5CF6",
            is_active = true
        )
        storage.saveAccounts(listOf(localAccount))

        // Remote payload with a different account
        val remoteAccount = AccountMeta(
            id = "acc_remote_work",
            email = "work@briefingos.com",
            display_name = "Work Remote",
            label = "WORK",
            color_hex = "#3B82F6",
            is_active = true
        )
        val inputData = BriefingData(
            accounts = listOf(remoteAccount)
        )

        val result = repository.enrichAccounts(inputData)

        // Both accounts must exist in result
        assertEquals(2, result.accounts.size)
        assertTrue(result.accounts.any { it.id == "acc_local_personal" && it.email == "local@briefingos.com" })
        assertTrue(result.accounts.any { it.id == "acc_remote_work" && it.email == "work@briefingos.com" })

        // Both accounts must also be persisted in storage
        val stored = storage.getAccounts()
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.id == "acc_local_personal" })
        assertTrue(stored.any { it.id == "acc_remote_work" })
    }

    @Test
    fun `enrichAccounts updates existing account details when incoming id matches`() {
        val original = AccountMeta(
            id = "acc_existing",
            email = "user@briefingos.com",
            display_name = "Old Display Name",
            label = "PERSONAL",
            color_hex = "#3B82F6",
            is_active = true
        )
        storage.saveAccounts(listOf(original))

        val updatedIncoming = AccountMeta(
            id = "acc_existing",
            email = "user@briefingos.com",
            display_name = "New Updated Name",
            label = "WORK",
            color_hex = "#10B981",
            is_active = false
        )
        val inputData = BriefingData(accounts = listOf(updatedIncoming))

        val result = repository.enrichAccounts(inputData)

        assertEquals(1, result.accounts.size)
        val account = result.accounts.first()
        assertEquals("acc_existing", account.id)
        assertEquals("New Updated Name", account.display_name)
        assertEquals("WORK", account.label)
        assertEquals("#10B981", account.color_hex)
        assertFalse(account.is_active)
    }

    @Test
    fun `enrichAccounts updates existing account when email matches even if id differs`() {
        val localAccount = AccountMeta(
            id = "acc_custom_id_123",
            email = "matched@example.com",
            display_name = "Original Name",
            label = "PERSONAL",
            color_hex = "#3B82F6",
            is_active = true
        )
        storage.saveAccounts(listOf(localAccount))

        val incomingWithSameEmail = AccountMeta(
            id = "incoming_different_id",
            email = "matched@example.com",
            display_name = "Synced Name",
            label = "DEV",
            color_hex = "#EC4899",
            is_active = true
        )
        val inputData = BriefingData(accounts = listOf(incomingWithSameEmail))

        val result = repository.enrichAccounts(inputData)

        assertEquals(1, result.accounts.size)
        val account = result.accounts.first()
        assertEquals("acc_custom_id_123", account.id)
        assertEquals("Synced Name", account.display_name)
        assertEquals("DEV", account.label)
        assertEquals("#EC4899", account.color_hex)
    }

    @Test
    fun `enrichAccounts creates default primary account if both storage and payload are empty`() {
        val emptyData = BriefingData(accounts = emptyList())

        val result = repository.enrichAccounts(emptyData)

        assertEquals(1, result.accounts.size)
        val primary = result.accounts.first()
        assertEquals("primary", primary.id)
        assertEquals("user@gmail.com", primary.email)
        assertEquals("Primary Account", primary.display_name)
        assertTrue(primary.is_active)

        // Verifies persisted into storage
        assertEquals(1, storage.getAccounts().size)
        assertEquals("primary", storage.getAccounts().first().id)
    }

    // =========================================================================
    // 2. filterByAccount() Tests
    // =========================================================================

    @Test
    fun `filterByAccount with null, blank, or ALL returns complete dataset`() {
        val testData = createSampleBriefingData()

        val resultNull = repository.filterByAccount(testData, null)
        val resultBlank = repository.filterByAccount(testData, "   ")
        val resultAll = repository.filterByAccount(testData, "ALL")

        assertEquals(testData, resultNull)
        assertEquals(testData, resultBlank)
        assertEquals(testData, resultAll)
    }

    @Test
    fun `filterByAccount accurately filters by account ID`() {
        val testData = createSampleBriefingData()

        val filtered = repository.filterByAccount(testData, "acc_work")

        // Important items
        assertEquals(1, filtered.morning_briefing.important.size)
        assertEquals("imp_work_1", filtered.morning_briefing.important.first().id)

        // Needs attention items
        assertEquals(1, filtered.morning_briefing.needs_attention.size)
        assertEquals("att_work_1", filtered.morning_briefing.needs_attention.first().id)

        // Actions
        assertEquals(1, filtered.morning_briefing.actions.size)
        assertEquals("act_work_1", filtered.morning_briefing.actions.first().id)

        // Email details
        assertEquals(1, filtered.email_details.size)
        assertTrue(filtered.email_details.containsKey("em_work_1"))
        assertFalse(filtered.email_details.containsKey("em_personal_1"))

        // Informational topics matching account_id
        assertEquals(1, filtered.morning_briefing.informational.size)
        assertEquals("Work Infrastructure", filtered.morning_briefing.informational.first().topic)
    }

    @Test
    fun `filterByAccount matches email details by account email address`() {
        val testData = createSampleBriefingData()

        // Filtering by acc_personal which has email "personal@domain.com"
        val filtered = repository.filterByAccount(testData, "acc_personal")

        assertEquals(1, filtered.email_details.size)
        val email = filtered.email_details["em_personal_1"]
        assertNotNull(email)
        assertEquals("personal@domain.com", email?.account_email)
    }

    @Test
    fun `filterByAccount handles legacy primary and default alias matching symmetrically`() {
        val accountMetaPrimary = AccountMeta(id = "primary", email = "p@example.com")
        val briefingWithDefaultItems = BriefingData(
            accounts = listOf(accountMetaPrimary),
            morning_briefing = MorningBriefing(
                important = listOf(
                    BriefingItem(id = "imp_def", account_id = "default", subject = "Default Item")
                ),
                needs_attention = listOf(
                    BriefingItem(id = "att_def", account_id = "default", subject = "Default Attention")
                ),
                actions = listOf(
                    ActionItem(id = "act_def", account_id = "default", action = "Do Action")
                )
            ),
            email_details = mapOf(
                "em_def" to EmailDetail(id = "em_def", account_id = "default", subject = "Default Mail")
            )
        )

        // Filter using "primary" should match items tagged with "default"
        val filteredByPrimary = repository.filterByAccount(briefingWithDefaultItems, "primary")
        assertEquals(1, filteredByPrimary.morning_briefing.important.size)
        assertEquals("imp_def", filteredByPrimary.morning_briefing.important.first().id)
        assertEquals(1, filteredByPrimary.morning_briefing.needs_attention.size)
        assertEquals(1, filteredByPrimary.morning_briefing.actions.size)
        assertEquals(1, filteredByPrimary.email_details.size)

        // Symmetrically: filter using "default" when item has "primary"
        val briefingWithPrimaryItems = BriefingData(
            accounts = listOf(AccountMeta(id = "default", email = "d@example.com")),
            morning_briefing = MorningBriefing(
                important = listOf(
                    BriefingItem(id = "imp_pri", account_id = "primary", subject = "Primary Item")
                )
            )
        )
        val filteredByDefault = repository.filterByAccount(briefingWithPrimaryItems, "default")
        assertEquals(1, filteredByDefault.morning_briefing.important.size)
        assertEquals("imp_pri", filteredByDefault.morning_briefing.important.first().id)
    }

    @Test
    fun `filterByAccount includes informational topics if topic contains an email from filtered emails`() {
        val testData = BriefingData(
            accounts = listOf(
                AccountMeta(id = "acc_dev", email = "dev@example.com")
            ),
            morning_briefing = MorningBriefing(
                informational = listOf(
                    // Topic has account_id = "other", but one of its email ids is in email_details for acc_dev
                    TopicItem(
                        topic = "Cross-account Topic",
                        ids = listOf("dev_email_10"),
                        account_id = "acc_unrelated"
                    ),
                    TopicItem(
                        topic = "Irrelevant Topic",
                        ids = listOf("other_email_20"),
                        account_id = "acc_unrelated"
                    )
                )
            ),
            email_details = mapOf(
                "dev_email_10" to EmailDetail(
                    id = "dev_email_10",
                    account_id = "acc_dev",
                    subject = "Dev Pull Request"
                )
            )
        )

        val filtered = repository.filterByAccount(testData, "acc_dev")

        assertEquals(1, filtered.morning_briefing.informational.size)
        assertEquals("Cross-account Topic", filtered.morning_briefing.informational.first().topic)
    }

    // =========================================================================
    // 3. Gson Deserialization Tests
    // =========================================================================

    @Test
    fun `Gson deserializes AccountMeta with snake_case and camelCase alternates`() {
        val jsonSnakeCase = """
            {
                "id": "acc_01",
                "email": "dev@briefingos.com",
                "display_name": "Dev Account",
                "label": "DEV",
                "color_hex": "#10B981",
                "is_active": true
            }
        """.trimIndent()

        val parsedSnake = gson.fromJson(jsonSnakeCase, AccountMeta::class.java)
        assertEquals("acc_01", parsedSnake.id)
        assertEquals("dev@briefingos.com", parsedSnake.email)
        assertEquals("Dev Account", parsedSnake.display_name)
        assertEquals("DEV", parsedSnake.label)
        assertEquals("#10B981", parsedSnake.color_hex)
        assertTrue(parsedSnake.is_active)

        // Test alternate alias fields (camelCase and alternate names)
        val jsonAlternates = """
            {
                "accountId": "acc_02",
                "emailAddress": "work@briefingos.com",
                "displayName": "Work Suite",
                "accountLabel": "WORK",
                "accountColorHex": "#3B82F6",
                "isActive": false
            }
        """.trimIndent()

        val parsedAlternates = gson.fromJson(jsonAlternates, AccountMeta::class.java)
        assertEquals("acc_02", parsedAlternates.id)
        assertEquals("work@briefingos.com", parsedAlternates.email)
        assertEquals("Work Suite", parsedAlternates.display_name)
        assertEquals("WORK", parsedAlternates.label)
        assertEquals("#3B82F6", parsedAlternates.color_hex)
        assertFalse(parsedAlternates.is_active)
    }

    @Test
    fun `Gson deserializes complete BriefingData JSON structure`() {
        val fullJson = """
        {
            "briefing_type": "morning",
            "generated_at": "2026-10-26T08:00:00Z",
            "accounts": [
                {
                    "id": "acc_personal_1",
                    "email": "me@domain.com",
                    "display_name": "My Personal Mail",
                    "label": "PERSONAL",
                    "color_hex": "#8B5CF6",
                    "is_active": true
                }
            ],
            "morning_briefing": {
                "important": [
                    {
                        "id": "msg_imp_1",
                        "sender": "Security <sec@domain.com>",
                        "subject": "Critical Alert",
                        "summary": "Urgent update required",
                        "action_required": true,
                        "account_id": "acc_personal_1",
                        "account_label": "PERSONAL",
                        "account_color_hex": "#8B5CF6"
                    }
                ],
                "needs_attention": [
                    {
                        "id": "msg_att_1",
                        "sender": "Billing <bill@domain.com>",
                        "subject": "Invoice Pending",
                        "summary": "Invoice #4928 is due",
                        "action_required": false,
                        "account_id": "acc_personal_1"
                    }
                ],
                "informational": [
                    {
                        "ids": ["msg_imp_1"],
                        "topic": "Security Updates",
                        "summary": "Summary of updates",
                        "account_id": "acc_personal_1"
                    }
                ],
                "actions": [
                    {
                        "id": "act_1",
                        "action": "Rotate API key",
                        "reason": "Security protocol",
                        "account_id": "acc_personal_1"
                    }
                ]
            },
            "email_details": {
                "msg_imp_1": {
                    "id": "msg_imp_1",
                    "sender": "Security <sec@domain.com>",
                    "subject": "Critical Alert",
                    "date": "2026-10-26",
                    "body": "Full message text body",
                    "account_id": "acc_personal_1",
                    "account_label": "PERSONAL",
                    "account_color_hex": "#8B5CF6",
                    "account_email": "me@domain.com"
                }
            }
        }
        """.trimIndent()

        val briefingData = gson.fromJson(fullJson, BriefingData::class.java)

        assertNotNull(briefingData)
        assertEquals("morning", briefingData.briefing_type)
        assertEquals("2026-10-26T08:00:00Z", briefingData.generated_at)
        assertEquals(1, briefingData.accounts.size)
        assertEquals("acc_personal_1", briefingData.accounts.first().id)

        // morning_briefing sections
        assertEquals(1, briefingData.morning_briefing.important.size)
        val imp = briefingData.morning_briefing.important.first()
        assertEquals("msg_imp_1", imp.id)
        assertTrue(imp.action_required)

        assertEquals(1, briefingData.morning_briefing.needs_attention.size)
        assertEquals(1, briefingData.morning_briefing.informational.size)
        assertEquals(1, briefingData.morning_briefing.actions.size)

        // email_details map
        assertEquals(1, briefingData.email_details.size)
        val email = briefingData.email_details["msg_imp_1"]
        assertNotNull(email)
        assertEquals("me@domain.com", email?.account_email)
        assertEquals("Full message text body", email?.body)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createSampleBriefingData(): BriefingData {
        val accountPersonal = AccountMeta(
            id = "acc_personal",
            email = "personal@domain.com",
            display_name = "Personal Mail",
            label = "PERSONAL",
            color_hex = "#8B5CF6"
        )
        val accountWork = AccountMeta(
            id = "acc_work",
            email = "work@company.com",
            display_name = "Work Mail",
            label = "WORK",
            color_hex = "#3B82F6"
        )

        return BriefingData(
            accounts = listOf(accountPersonal, accountWork),
            morning_briefing = MorningBriefing(
                important = listOf(
                    BriefingItem(id = "imp_personal_1", account_id = "acc_personal", subject = "Doctor Appointment"),
                    BriefingItem(id = "imp_work_1", account_id = "acc_work", subject = "Production Deployment")
                ),
                needs_attention = listOf(
                    BriefingItem(id = "att_personal_1", account_id = "acc_personal", subject = "Package Delivery"),
                    BriefingItem(id = "att_work_1", account_id = "acc_work", subject = "Sprint Review")
                ),
                informational = listOf(
                    TopicItem(topic = "Personal Health", account_id = "acc_personal", ids = listOf("em_personal_1")),
                    TopicItem(topic = "Work Infrastructure", account_id = "acc_work", ids = listOf("em_work_1"))
                ),
                actions = listOf(
                    ActionItem(id = "act_personal_1", account_id = "acc_personal", action = "Confirm booking"),
                    ActionItem(id = "act_work_1", account_id = "acc_work", action = "Review pull request")
                )
            ),
            email_details = mapOf(
                "em_personal_1" to EmailDetail(
                    id = "em_personal_1",
                    account_id = "acc_personal",
                    account_email = "personal@domain.com",
                    subject = "Doctor Appointment"
                ),
                "em_work_1" to EmailDetail(
                    id = "em_work_1",
                    account_id = "acc_work",
                    account_email = "work@company.com",
                    subject = "Production Deployment"
                )
            )
        )
    }
}
