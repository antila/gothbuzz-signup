package eu.symmetrysought.gothbuzz

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.workflows.executions.v1.*
import com.google.gson.Gson
import com.sun.nio.sctp.NotificationHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutionException

class NotificationHandler private constructor() {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val credentials: GoogleCredentials
        get() = GoogleCredentials.fromStream(Glob.GOTHBUZZ_WORKFLOW_EXEC.byteInputStream())
                .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

    private val executionSettings: ExecutionsSettings
        get() = ExecutionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()
    enum class NotificationChannel {
        ANNOUNCEMENTS, ERRORS, BUZZ
    }


    fun propagateAnnouncement(message: String) {
        workflowsExecution(Glob.GOTHBUZZ_PROPAGATOR, mapOf("message" to message, "channel" to NotificationChannel.ANNOUNCEMENTS.name.lowercase()))
    }


    fun propagateBuzz(message: String) {
        workflowsExecution(Glob.GOTHBUZZ_PROPAGATOR, mapOf("message" to message, "channel" to NotificationChannel.BUZZ.name.lowercase()))
    }


    fun propagateError(message: String) {
        workflowsExecution(Glob.GOTHBUZZ_PROPAGATOR, mapOf("message" to message, "channel" to NotificationChannel.ERRORS.name.lowercase()))
    }


    fun propagateError(t: Throwable) {
        val message = t.message
        val lineNumber = t.stackTrace.get(0).lineNumber.toString()
        val classname = t.stackTrace.get(0).className.toString()
        val filename = t.stackTrace.get(0).fileName
        val function = t.stackTrace.get(0).methodName

        propagateError("""$classname.$function threw message "$message" in $filename:$lineNumber""")
    }


    @Throws(IOException::class, InterruptedException::class, ExecutionException::class)
    private fun workflowsExecution(workflowId: String, fields: Map<String, String>) {
        //https://cloud.google.com/workflows/docs/executing-workflow#client-libraries
        val body = Gson().toJson(fields)
        ExecutionsClient.create(executionSettings).use { executionsClient ->
            // Construct the fully qualified location path.
            val parent: WorkflowName = WorkflowName.of(Glob.GOTHBUZZ_PROJECT_ID, Glob.GOTHBUZZ_GOOGLE_LOCATION_ID, workflowId)

            // Creates the execution object.
            val request: CreateExecutionRequest = CreateExecutionRequest.newBuilder()
                .setParent(parent.toString())
                .setExecution(Execution
                    .newBuilder()
                    .setArgument(body)
                    .build())
                .build()
            executionsClient.createExecution(request)

            val response: Execution = executionsClient.createExecution(request)
            val executionName: String = response.getName()
            logger.info("Created execution: $executionName")
            var backoffTime: Long = 0
            var backoffDelay: Long = 1000 // Start wait with delay of 1,000 ms
            val backoffTimeout = (10 * 60 * 1000).toLong() // Time out at 10 minutes

            // Wait for execution to finish, then print results.
            logger.info("Poll for results...")
            var finished = false
            while (!finished && backoffTime < backoffTimeout) {
                val execution: Execution = executionsClient.getExecution(executionName)
                finished = execution.getState() !== Execution.State.ACTIVE

                // If we haven't seen the results yet, wait.
                if (!finished) {
                    logger.info("- Waiting for results")
                    Thread.sleep(backoffDelay)
                    backoffTime += backoffDelay
                    backoffDelay *= 2 // Double the delay to provide exponential backoff.
                } else {
                    logger.info("Execution finished with state: ${execution.getState().name}")
                    logger.info("Execution results: ${execution.getResult()}")
                }
            }
        }
    }
    companion object {
        fun newBuilder(): Builder {
            return Builder()
        }
        // THis function should not take envvars from Glob
        fun notifyInitializationError(e: ExceptionInInitializerError) {
            val logger: Logger = LoggerFactory.getLogger(NotificationHandler::class.java)
            val env = System.getenv("GOTHBUZZ_ENVIRONMENT_NAME")
            val message = e.message
            val lineNumber = e.stackTrace.get(0).lineNumber.toString()
            val classname = e.stackTrace.get(0).className.toString()
            val filename = e.stackTrace.get(0).fileName
            val function = e.stackTrace.get(0).methodName
            val content = """$classname.$function threw message "$message" in $filename:$lineNumber"""

            logger.warn(content)

            if ("local" == env) {
                return
            }

            val prodErrorsWebhook = System.getenv("PROD_ERRORS_WEBHOOK")

            if (null == prodErrorsWebhook) {
                logger.warn("Could not initialize startup error notifications properly. Quiting!")
                return
            }

            webhookHttpPost(prodErrorsWebhook, """{"content": "${content.replace("\"", "'")}"}""")
        }

        private fun webhookHttpPost(prodErrorsWebhookUrl: String, body: String) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(prodErrorsWebhookUrl))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            val client = HttpClient.newHttpClient()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    class Builder {
        fun build() = NotificationHandler()
    }
}