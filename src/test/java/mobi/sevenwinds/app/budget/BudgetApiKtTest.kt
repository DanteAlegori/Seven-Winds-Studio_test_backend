package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat

class BudgetApiKtTest : ServerTest() {

    @BeforeEach
    internal fun setUp() {
        transaction {
            BudgetTable.deleteAll()
            AuthorTable.deleteAll()
        }
    }

    fun LocalDateTime.formatToString(): String {
        val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")
        return formatter.print(this)
    }

    @Test
    fun testAuthorAdd() {
        val author = AuthorRecord("Test Author")
        val createdAuthor = RestAssured.given()
            .jsonBody(author)
            .post("/author/add")
            .toResponse<AuthorRecord>()

        Assert.assertEquals(author.fullName, createdAuthor.fullName)

        val author2 = AuthorRecord("Test Author 2")
        val createdAuthor2 = RestAssured.given()
            .jsonBody(author2)
            .post("/author/add")
            .toResponse<AuthorRecord>()

        Assert.assertEquals(author2.fullName, createdAuthor2.fullName)

    }

    @Test
    fun testBudgetPagination() {
        addRecord(BudgetRecord(2020, 5, 10, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 20, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 40, BudgetType.Приход))
        addRecord(BudgetRecord(2030, 1, 1, BudgetType.Расход))

        RestAssured.given()
            .queryParam("limit", 3)
            .queryParam("offset", 1)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(5, response.total)
                Assert.assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }

    @Test
    fun testStatsSortOrder() {
        addRecord(BudgetRecord(2020, 5, 100, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 400, BudgetType.Приход))

        // expected sort order - month ascending, amount descending

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0&sort_by=month&order=asc")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                Assert.assertEquals(5, response.items[0].amount)
                Assert.assertEquals(30, response.items[1].amount)
                Assert.assertEquals(100, response.items[2].amount)
                Assert.assertEquals(50, response.items[3].amount)
                Assert.assertEquals(400, response.items[4].amount)

            }
        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0&sort_by=amount&order=desc")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                Assert.assertEquals(400, response.items[0].amount)
                Assert.assertEquals(100, response.items[1].amount)
                Assert.assertEquals(50, response.items[2].amount)
                Assert.assertEquals(30, response.items[3].amount)
                Assert.assertEquals(5, response.items[4].amount)
            }
    }


    @Test
    fun testBudgetFilterByAuthor() {
        val author = addAuthor("Test Author")
        addRecord(BudgetRecord(2020, 5, 100, BudgetType.Приход, author.id.value))
        addRecord(BudgetRecord(2020, 1, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 400, BudgetType.Приход))

        RestAssured.given()
            .queryParam("limit", 100)
            .queryParam("offset", 0)
            .queryParam("author_name", "Test")
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                Assert.assertEquals(1, response.items.size)
                Assert.assertEquals(100, response.items[0].amount)
                Assert.assertEquals("Test Author", response.items.firstOrNull()?.author?.fullName)
            }

        RestAssured.given()
            .queryParam("limit", 100)
            .queryParam("offset", 0)
            .queryParam("author_name", "Test Author")
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                Assert.assertEquals(1, response.items.size)
                Assert.assertEquals(100, response.items[0].amount)
                Assert.assertEquals("Test Author", response.items.firstOrNull()?.author?.fullName)
            }

        RestAssured.given()
            .queryParam("limit", 100)
            .queryParam("offset", 0)
            .queryParam("author_name", "NonExisted")
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                Assert.assertEquals(0, response.items.size)
            }
    }

    @Test
    fun testBudgetAddWithAuthor() {
        val author = addAuthor("Test Author")
        val record = BudgetRecord(2020, 5, 10, BudgetType.Приход, author.id.value)
        val createdRecord = addRecord(record)
        Assert.assertEquals(record.authorId, createdRecord.authorId)

        RestAssured.given()
            .queryParam("limit", 100)
            .queryParam("offset", 0)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                Assert.assertEquals("Test Author", response.items.firstOrNull()?.author?.fullName)
            }
    }


    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRecord(2020, -5, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRecord(2020, 15, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)
    }

    private fun addAuthor(name: String): AuthorEntity {
        return RestAssured.given()
            .jsonBody(AuthorRecord(name))
            .post("/author/add")
            .toResponse<AuthorRecord>().let { response ->
                transaction {
                    AuthorEntity.find { AuthorTable.fullName eq response.fullName }.first()
                }
            }
    }


    private fun addRecord(record: BudgetRecord): BudgetRecord {
        return RestAssured.given()
            .jsonBody(record)
            .post("/budget/add")
            .toResponse()
    }
}
