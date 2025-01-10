package mobi.sevenwinds.modules

import com.papsign.ktor.openapigen.openAPIGen
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.tag
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import mobi.sevenwinds.app.budget.budget
import mobi.sevenwinds.app.budget.AuthorRecord
import mobi.sevenwinds.app.budget.BudgetRecord


import mobi.sevenwinds.app.budget.BudgetService
import io.ktor.request.*


fun NormalOpenAPIRoute.swaggerRouting() {
    tag(SwaggerTag.Бюджет) { budget() }
}

fun Routing.serviceRouting() {
    get("/") {
        call.respondRedirect("/swagger-ui/index.html?url=/openapi.json", true)
    }

    get("/openapi.json") {
        call.respond(application.openAPIGen.api.serialize())
    }

    route("/budget") {
        post("/author/add") {
            val record = call.receive<AuthorRecord>()
            val response = BudgetService.addAuthor(record)
            call.respond(response)
        }

        post("/add") {
            val record = call.receive<BudgetRecord>()
            val response = BudgetService.addRecord(record)
            call.respond(response)
        }

    }
}

