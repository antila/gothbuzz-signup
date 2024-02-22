package eu.symmetrysought.gothbuzz

import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

@Controller("/")
class IndexController {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    @Produces(MediaType.TEXT_HTML)
    @Get
    fun index(request: HttpRequest<*>): HttpResponse<*> {
        logger.info("Got a visit to /...")

        return when (request.path) {
            "/", "/index.html", "/index.htm" -> {
                val base = IndexController::class.java.getResource("/web/base.html")!!.readText()
                val data = IndexController::class.java.getResource("/web/index.json")!!.readText()
                val body = Glob.applyTemplate(base, data)
                HttpResponse.ok(body).contentType(MediaType.TEXT_HTML)
            }
            "/robots.txt" -> {
                val body = IndexController::class.java.getResource("/static/robots.txt")!!.readText()
                HttpResponse.ok(body).contentType(MediaType.TEXT_HTML)
            }
            else -> HttpResponse.redirect<HttpResponse<String>>(URI.create("/404/")).status(HttpStatus.MOVED_PERMANENTLY)
        }
    }


    @Produces(MediaType.TEXT_HTML)
    @Get(value = "/filename={filename}")
    fun indexWithParam(@NonNull filename: String, request: HttpRequest<*>): HttpResponse<*> {
        logger.info("Got a visit to /$filename...")

        return when (filename) {
            "index.html", "index.htm" -> {
                val base = IndexController::class.java.getResource("/web/base.html")!!.readText()
                val data = IndexController::class.java.getResource("/web/index.json")!!.readText()
                val body = Glob.applyTemplate(base, data)
                HttpResponse.ok(body).contentType(MediaType.TEXT_HTML)
            }
            "robots.txt" -> {
                val body = IndexController::class.java.getResource("/static/robots.txt")!!.readText()
                HttpResponse.ok(body).contentType(MediaType.TEXT_HTML)
            }
            else -> HttpResponse.redirect<HttpResponse<String>>(URI.create("/404/")).status(HttpStatus.MOVED_PERMANENTLY)
        }
    }
}
