package maestro.cli.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import maestro.cli.App
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import picocli.CommandLine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class McpCommandTest {

    private lateinit var commandLine: CommandLine
    private val originalIn = System.`in`
    private val originalOut = System.out
    private val out = ByteArrayOutputStream()
    private lateinit var pipedIn: PipedInputStream
    private lateinit var pipedOut: PipedOutputStream

    @BeforeEach
    fun setUp() {
        commandLine = CommandLine(App())
        System.setOut(PrintStream(out))
        pipedOut = PipedOutputStream()
        pipedIn = PipedInputStream(pipedOut)
        System.setIn(pipedIn)
    }

    @AfterEach
    fun tearDown() {
        System.setIn(originalIn)
        System.setOut(originalOut)
        pipedIn.close()
        pipedOut.close()
    }

    @Test
    fun `mcp command starts without device argument`() {
        thread {
            commandLine.execute("mcp")
        }
        // Give the server a moment to start
        Thread.sleep(1000)
        assertTrue(out.toString().contains("MCP server starting on stdio..."))
    }

    @Test
    fun `mcp command starts with device argument`() {
        thread {
            commandLine.execute("mcp", "--device", "emulator-5554")
        }
        // Give the server a moment to start
        Thread.sleep(1000)
        assertTrue(out.toString().contains("MCP server starting on stdio..."))
        // Note: We can't easily verify the deviceId is used without deeper instrumentation or logs
        // For now, we just check that the server starts.
    }

    // Note: Testing the actual tool calls (hierarchy, test) is more complex
    // as it requires a running Maestro driver and device.
    // These tests would be more like integration tests.
    // For now, we'll focus on the command starting correctly.
    //
    // Example of how a tool call test might look (conceptual):
    //
    // @Test
    // fun `mcp hierarchy tool call`() {
    //     thread {
    //         commandLine.execute("mcp")
    //     }
    //     Thread.sleep(1000) // Wait for server to start
    //
    //     val objectMapper = jacksonObjectMapper()
    //     val request = mapOf(
    //         "jsonrpc" to "2.0",
    //         "method" to "tool_call",
    //         "params" to mapOf(
    //             "tool_name" to "hierarchy",
    //             "input" to mapOf<String, Any>()
    //         ),
    //         "id" to "1"
    //     )
    //     val requestJson = objectMapper.writeValueAsString(request) + ""
    //
    //     // Simulate sending the request to the server's stdin
    //     pipedOut.write(requestJson.toByteArray())
    //     pipedOut.flush()
    //
    //     // Read the response from the server's stdout
    //     // This part needs careful handling of blocking reads and timeouts
    //     val responseJson = // ... read from 'out' ...
    //     val responseMap = objectMapper.readValue<Map<String, Any>>(responseJson)
    //
    //     // Assertions on the response
    //     assertEquals("2.0", responseMap["jsonrpc"])
    //     assertNotNull(responseMap["result"])
    //     // ... further assertions on the hierarchy data ...
    // }
}
