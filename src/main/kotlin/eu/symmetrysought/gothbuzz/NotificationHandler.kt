package eu.symmetrysought.gothbuzz

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.workflows.executions.v1.*
import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ExecutionException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class NotificationHandler(private val projectId: String,
                          private val locationId: String,
                          private val workflowId: String) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    companion object {
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

        @Throws(IOException::class, InterruptedException::class, ExecutionException::class)
        private fun workflowsQuickstart(String, payload: String) {
            val logger: Logger = LoggerFactory.getLogger(NotificationHandler::class.java)
            //https://cloud.google.com/workflows/docs/executing-workflow#client-libraries

            val credentials =
                GoogleCredentials.fromStream(System.getenv("GOTHBUZZ_WORKFLOW_EXEC").byteInputStream())
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

            val settings = ExecutionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            //val smidigt = mutableMapOf("x-goog-api-key" to System.getenv("GOOGLE_WORKFLOW_READ_APIKEY")!!).entries.first()
            ExecutionsClient.create(settings).use { executionsClient ->
                // Construct the fully qualified location path.
                val parent: WorkflowName = WorkflowName.of(projectId, locationId, workflowId)

                // Creates the execution object.
                val request: CreateExecutionRequest = CreateExecutionRequest.newBuilder()
                    .setParent(parent.toString())
                    .setExecution(Execution
                        .newBuilder()
                        .setArgument(payload)
                        .build())
                    .build()
                executionsClient.createExecution(request)
                /*
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

                 */
            }
        }
    }
}