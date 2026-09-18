package se.laz.casual.example.front;

import org.junit.jupiter.api.Test;
import jakarta.ws.rs.core.Response;
import se.laz.casual.api.CasualRuntimeException;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.ServiceReturnState;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import static org.junit.jupiter.api.Assertions.*;

class CounterResourceTest
{
    @Test
    void retriesServiceFailureAndPreservesPayload() throws Exception
    {
        verify(200, 2, ErrorState.TPESVCFAIL, ErrorState.OK);
    }

    @Test
    void stopsAfterThreeAttempts() throws Exception
    {
        verify(503, 3, ErrorState.TPESVCFAIL, ErrorState.TPESVCFAIL, ErrorState.TPESVCFAIL, ErrorState.OK);
    }

    @Test
    void doesNotRetryOtherResults() throws Exception
    {
        verify(200, 1, ErrorState.OK);
        verify(503, 1, ErrorState.TPENOENT);
        verify(503, 1, ErrorState.TPESVCERR);
    }

    @Test
    void doesNotRetryInvocationExceptions() throws Exception
    {
        verify(503, 1);
    }

    @Test
    void interruptionStopsRetryAndPreservesInterruptStatus() throws Exception
    {
        ScriptedCall call = new ScriptedCall(ErrorState.TPESVCFAIL, ErrorState.OK);
        Thread.currentThread().interrupt();
        try (Response response = new CounterResource(call).serviceRequest("counter",
                new ByteArrayInputStream(new byte[]{1, 2, 3})))
        {
            assertEquals(503, response.getStatus());
            assertEquals("Retry interrupted", response.getEntity());
            assertEquals(1, call.calls);
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally
        {
            Thread.interrupted();
        }
    }

    private void verify(int status, int attempts, ErrorState... results) throws Exception
    {
        ScriptedCall call = new ScriptedCall(results);
        try (Response response = new CounterResource(call).serviceRequest("counter",
                new ByteArrayInputStream(new byte[]{1, 2, 3})))
        {
            assertEquals(status, response.getStatus());
            assertEquals(attempts, call.calls);
            if (status == 200)
            {
                assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) response.getEntity());
            }
        }
    }

    private static class ScriptedCall extends TransactionalCounterCall
    {
        private final Queue<ErrorState> results;
        private int calls;

        private ScriptedCall(ErrorState... results)
        {
            super(null, null);
            this.results = new ArrayDeque<>(Arrays.asList(results));
        }

        @Override
        public ServiceReturn<CasualBuffer> call(String serviceName, CasualBuffer buffer)
        {
            calls++;
            assertEquals("counter", serviceName);
            assertArrayEquals(new byte[]{1, 2, 3}, buffer.getBytes().get(0));
            if (results.isEmpty())
            {
                throw new CasualRuntimeException("Invocation failed");
            }
            ErrorState state = results.remove();
            return new ServiceReturn<>(buffer,
                    state == ErrorState.OK ? ServiceReturnState.TPSUCCESS : ServiceReturnState.TPFAIL, state, 0);
        }
    }
}
