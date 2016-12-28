package org.camunda.tngp.broker.taskqueue.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.broker.util.mocks.WfRuntimeEvents;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InputActivityInstanceHandlerTest
{

    @Mock
    protected ResourceContextProvider<TaskQueueContext> taskQueueContextProvider;

    @Mock
    protected TaskQueueContext taskQueueContext;

    protected StubLogWriter logWriter;
    protected StubLogWriters logWriters;
    protected StubResponseControl responseControl;
    protected IdGenerator idGenerator;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logWriter = new StubLogWriter();
        logWriters = new StubLogWriters(0);
        logWriters.addWriter(3, logWriter);
        responseControl = new StubResponseControl();
        idGenerator = new PrivateIdGenerator(10L);

        when(taskQueueContextProvider.getContextForResource(3)).thenReturn(taskQueueContext);
        when(taskQueueContext.getLogWriter()).thenReturn(logWriter);
        when(taskQueueContext.getTaskInstanceIdGenerator()).thenReturn(idGenerator);
    }

    @Test
    public void shouldHandleActivityInstanceCreateEvent()
    {
        // given
        final BpmnActivityEventReader activityInstanceEvent = WfRuntimeEvents.mockActivityInstanceEvent(ExecutionEventType.ACT_INST_CREATED);

        final InputActivityInstanceHandler handler = new InputActivityInstanceHandler(taskQueueContextProvider);

        // when
        handler.handle(activityInstanceEvent, responseControl, logWriters);

        // then
        assertThat(logWriter.size()).isEqualTo(1);

        final TaskInstanceReader taskInstanceReader = logWriter.getEntryAs(0, TaskInstanceReader.class);

        assertThat(taskInstanceReader.id()).isEqualTo(11L);
        assertThatBuffer(taskInstanceReader.getTaskType()).hasBytes(WfRuntimeEvents.TASK_TYPE);
        assertThat(taskInstanceReader.wfRuntimeResourceId()).isEqualTo(WfRuntimeEvents.WF_RUNTIME_LOG_ID);
        assertThat(taskInstanceReader.wfActivityInstanceEventKey()).isEqualTo(WfRuntimeEvents.KEY);
        assertThat(taskInstanceReader.wfInstanceId()).isEqualTo(WfRuntimeEvents.PROCESS_INSTANCE_ID);
    }

}
