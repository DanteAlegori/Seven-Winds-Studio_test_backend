package mobi.sevenwinds.app.budget

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.LocalDateTime

fun DateTime.formatToString(): String {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")
    return formatter.print(this)
}

object AuthorTable : IntIdTable("author") {
    val fullName = varchar("full_name", 255)
    val createdAt = datetime("created_at")
}

class AuthorEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AuthorEntity>(AuthorTable)

    var fullName by AuthorTable.fullName
    var createdAt by AuthorTable.createdAt

    fun toResponse(): AuthorRecord {
        return AuthorRecord(fullName)
    }

    fun toInfoResponse(): AuthorInfo {
        return AuthorInfo(fullName, createdAt.formatToString())
    }
}

object BudgetTable : IntIdTable("budget") {
    val year = integer("year")
    val month = integer("month")
    val amount = integer("amount")
    val type = enumerationByName("type", 100, BudgetType::class)
    val authorId = reference("author_id", AuthorTable, ReferenceOption.SET_NULL).nullable()
}

class BudgetEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BudgetEntity>(BudgetTable)

    var year by BudgetTable.year
    var month by BudgetTable.month
    var amount by BudgetTable.amount
    var type by BudgetTable.type
    var authorId by BudgetTable.authorId
    var author by AuthorEntity optionalReferencedOn BudgetTable.authorId

    fun toResponse(): BudgetRecord {
        return BudgetRecord(year, month, amount, type, authorId?.value)
    }

    fun toResponseWithAuthor(): BudgetRecordWithAuthor {
        val authorInfo = author?.toInfoResponse()
        return BudgetRecordWithAuthor(year, month, amount, type, authorInfo)
    }
}
