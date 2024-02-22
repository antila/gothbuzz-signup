package eu.symmetrysought.gothbuzz

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.MediaType
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.PathVariable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

@Controller
class VerifyController {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val emailHandler = EmailHandler()


    @Get(value = "/verify/{code}")
    fun verify(@NonNull code: String): HttpResponse<*> {
        logger.info("Got a visit to /verify/$code...")
        val result = emailHandler.verifyCode(code)

        return if (result.isSuccess) {
            HttpResponse.redirect<HttpResponse<String>>(URI.create("/verified/")).status(HttpStatus.MOVED_PERMANENTLY)
        }
        else {
            val ret = VerificationFailedReturnMessage("We could not find the code supplied in our database!")
            HttpResponse.badRequest(ret).contentType(MediaType.APPLICATION_JSON)
        }
    }

    @Produces(MediaType.TEXT_HTML)
    @Get("/verified/")
    fun verified(): String {
        logger.info("Got a visit to /verified...")
        val base = IndexController::class.java.getResource("/web/base.html")!!.readText()
        val data = IndexController::class.java.getResource("/web/verified.json")!!.readText()
        val ret = Glob.applyTemplate(base, data)

        return ret
    }
}


@Introspected
data class VerificationFailedReturnMessage(val returnMessage: String)
