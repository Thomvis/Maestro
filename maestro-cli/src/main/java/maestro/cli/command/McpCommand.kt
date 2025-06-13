package maestro.cli.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.Tool
import maestro.cli.App
import maestro.cli.DisableAnsiMixin
import maestro.cli.ShowHelpMixin
import maestro.cli.runner.TestRunner
import maestro.cli.runner.resultview.ResultView
import maestro.cli.runner.resultview.UiState
import maestro.cli.session.MaestroSessionManager
import maestro.cli.session.MaestroSessionManager.MaestroSession
import picocli.CommandLine
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "mcp",
    description = ["Starts a Maestro MCP server on stdio using the Java SDK"]
)
class McpCommand : Callable<Int> {

    @CommandLine.Mixin
    var disableANSIMixin: DisableAnsiMixin? = null

    @CommandLine.Mixin
    var showHelpMixin: ShowHelpMixin? = null

    @CommandLine.ParentCommand
    private val parent: App? = null

    @CommandLine.Option(
        names = ["--device", "--udid"],
        description = ["(Optional) Device ID to connect to"]
    )
    private var deviceId: String? = null

    private val objectMapper = jacksonObjectMapper()
    private val transportProvider = StdioServerTransportProvider(objectMapper)

    override fun call(): Int {
        println("MCP server (Java SDK) starting on stdio...")

        var currentSession: MaestroSession? = null
        fun getSession(): MaestroSession {
            println("getSession...")
            val session = currentSession
            if (session == null || session.maestro.isShutDown()) {
                return newSession().also {
                    currentSession = it
                }
            }
            return session
        }

        val emptyJsonSchema = """
            {
                "${'$'}schema": "http://json-schema.org/draft-07/schema#",
                "type": "object",
                "properties": {}
            }
            """

        val hierarchyTool = McpServerFeatures.SyncToolSpecification(
            Tool("hierarchy", "Get the view hierarchy of the connected device", emptyJsonSchema),
            { _, _ ->
                val hierarchy = getSession().maestro.viewHierarchy().root
                val hierarchyJson = objectMapper.writeValueAsString(hierarchy)
                println("Got hierarchy")
                CallToolResult(hierarchyJson, false)
            }
        )

        val testToolJsonSchema = """
            {
                "${'$'}schema": "http://json-schema.org/draft-07/schema#",
                "type": "object",
                "properties": {
                    "flow": {
                        "type": "string",
                        "description": "The Maestro flow to execute, as a YAML string."
                    }
                },
                "required": ["flow"]
            }
        """.trimIndent()

        val testTool = McpServerFeatures.SyncToolSpecification(
            Tool(
                "test",
                "Run a Maestro test flow",
                testToolJsonSchema
            ),
            { _, args ->
                val flowContent = args["flow"] as? String
                    ?: throw RuntimeException("Missing or invalid flow argument")

                val flowFile = Files.createTempFile("maestro-mcp-flow-", ".yaml").toFile().apply {
                    deleteOnExit()
                    writeText(flowContent)
                }

                val resultView = McpResultView()
                val tempDebugDir = Files.createTempDirectory("maestro-mcp-debug-")
                val result = TestRunner.runSingle(
                    maestro = getSession().maestro,
                    device = getSession().device,
                    flowFile = flowFile,
                    env = emptyMap(),
                    resultView = resultView,
                    debugOutputPath = tempDebugDir
                )
                val outputMap = if (result == 0) {
                    mapOf("status" to "success", "message" to "Test completed successfully")
                } else {
                    mapOf("status" to "failure", "message" to "Test execution failed: ${'$'}{resultView.output}")
                }
                CallToolResult(objectMapper.writeValueAsString(outputMap), false)
            }
        )

        val server = McpServer.sync(transportProvider)
            .serverInfo("maestro-mcp-server", parent?.getCliVersion() ?: "unknown")
            .tools(hierarchyTool, testTool)
            .build()

        Thread.currentThread().join()
        return 0
    }

    private fun newSession(): MaestroSession {
        println("newSession will")
        return MaestroSessionManager.newSession(
            host = parent?.host,
            port = parent?.port,
            driverHostPort = null,
            deviceId = deviceId,
            platform = parent?.platform,
        ) { session ->
            println("newSession did")
            session
        }
    }
}

private class McpResultView : ResultView {
    var output: String = ""

    override fun setState(state: UiState) {
        if (state is UiState.Error) {
            output += state.message + "\n"
        }
    }
}