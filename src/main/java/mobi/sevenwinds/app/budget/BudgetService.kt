package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.time.LocalDateTime


object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val authorEntity = body.authorId?.let { AuthorEntity.findById(it) }
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = authorEntity
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun addAuthor(body: AuthorRecord): AuthorRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = AuthorEntity.new {
                this.fullName = body.fullName
                this.createdAt = DateTime.now()
            }

            return@transaction entity.toResponse()
        }
    }


    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            var query = BudgetTable
                .join(AuthorTable, JoinType.LEFT, additionalConstraint = { BudgetTable.authorId eq AuthorTable.id })
                .slice(BudgetTable.columns)
                .select { BudgetTable.year eq param.year }

            if (param.author_name != null) {
                query = query.andWhere { AuthorTable.fullName like "%${param.author_name}%" }
            }
            val total = query.count()

            val allData = BudgetEntity.wrapRows(query).map { it.toResponseWithAuthor() }
            val sumByType = allData.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            var sortExpression: Expression<*>? = null
            if (param.sort_by != null) {
                sortExpression = when (param.sort_by) {
                    "month" -> BudgetTable.month
                    "amount" -> BudgetTable.amount
                    else -> null
                }
            }
            if (sortExpression != null) {
                query = if (param.order == "desc") query.orderBy(sortExpression to SortOrder.DESC) else query.orderBy(
                    sortExpression to SortOrder.ASC
                )
            }

            query = query.limit(param.limit, param.offset)
            val data = BudgetEntity.wrapRows(query).map { it.toResponseWithAuthor() }
            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }


}
