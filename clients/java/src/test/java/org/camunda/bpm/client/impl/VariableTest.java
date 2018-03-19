/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javassist.compiler.ast.Variable;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.exception.UnsupportedTypeException;
import org.camunda.bpm.client.helper.ClosableHttpClientMock;
import org.camunda.bpm.client.helper.MockProvider;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.task.impl.dto.CompleteRequestDto;
import org.camunda.bpm.client.task.impl.dto.TypedValueDto;
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.type.PrimitiveValueType;
import org.camunda.bpm.engine.variable.value.BooleanValue;
import org.camunda.bpm.engine.variable.value.BytesValue;
import org.camunda.bpm.engine.variable.value.DateValue;
import org.camunda.bpm.engine.variable.value.DoubleValue;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.LongValue;
import org.camunda.bpm.engine.variable.value.ShortValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * @author Tassilo Weidner
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, RequestExecutor.class})
public class VariableTest {

  private CloseableHttpResponse closeableHttpResponse;

  @Before
  public void setUp() throws JsonProcessingException {
    mockStatic(HttpClients.class);

    HttpClientBuilder httpClientBuilderMock = mock(HttpClientBuilder.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(HttpClients.custom())
      .thenReturn(httpClientBuilderMock);

    closeableHttpResponse = mock(CloseableHttpResponse.class);
    Mockito.when(closeableHttpResponse.getStatusLine())
      .thenReturn(mock(StatusLine.class));

    CloseableHttpClient httpClient = spy(new ClosableHttpClientMock(closeableHttpResponse));
    Mockito.when(httpClientBuilderMock.build())
      .thenReturn(httpClient);
  }
  
  /* tests if response of fetch and lock is deserialized properly */
  
  @Test
  public void shouldRetrieveAllVariablesUntypedFromEngine() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createLockedTask()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    final List<ExternalTask> externalTaskReference = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference.add(externalTask);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference.get(0);

    assertAllVariablesUntyped(externalTask);
  }

  @Test
  public void shouldRetrieveAllVariablesTypedFromEngine() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createLockedTask()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    final List<ExternalTask> externalTaskReference = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference.add(externalTask);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference.get(0);

    assertAllVariablesTyped(externalTask);
  }

  @Test
  public void shouldRetrieveSingleVariableUntypedFromEngine() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createLockedTask()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    final List<ExternalTask> externalTaskReference = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference.add(externalTask);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference.get(0);

    assertSingleVariableUntyped(externalTask);
  }

  @Test
  public void shouldRetrieveSingleVariableTypedFromEngine() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createLockedTask()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    final List<ExternalTask> externalTaskReference = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference.add(externalTask);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference.get(0);

    assertSingleVariableTyped(externalTask);
  }

  /* test if complete request is serialized properly */
  
  @Test
  public void shouldSetAllVariablesUntypedAccordingToCompleteRequest() throws Exception {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, Object> untypedVariables = new HashMap<>();
          untypedVariables.put(MockProvider.BOOLEAN_VARIABLE_NAME, MockProvider.BOOLEAN_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.SHORT_VARIABLE_NAME, MockProvider.SHORT_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.INTEGER_VARIABLE_NAME, MockProvider.INTEGER_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.LONG_VARIABLE_NAME, MockProvider.LONG_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.STRING_VARIABLE_NAME, MockProvider.STRING_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.DOUBLE_VARIABLE_NAME, MockProvider.DOUBLE_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.DATE_VARIABLE_NAME, MockProvider.DATE_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.BYTES_VARIABLE_NAME, MockProvider.BYTES_VARIABLE_VALUE);
          untypedVariables.put(MockProvider.NULL_VARIABLE_NAME, null);
          externalTask.setAllVariables(untypedVariables);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    assertCompleteRequestSerialization(objectMapper);
  }

  @Test
  public void shouldSetAllVariablesTypedAccordingToCompleteRequest() throws Exception {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, TypedValue> typedVariables = new HashMap<>();
          typedVariables.put(MockProvider.BOOLEAN_VARIABLE_NAME, Variables.booleanValue(MockProvider.BOOLEAN_VARIABLE_VALUE));
          typedVariables.put(MockProvider.SHORT_VARIABLE_NAME, Variables.shortValue(MockProvider.SHORT_VARIABLE_VALUE));
          typedVariables.put(MockProvider.INTEGER_VARIABLE_NAME, Variables.integerValue(MockProvider.INTEGER_VARIABLE_VALUE));
          typedVariables.put(MockProvider.LONG_VARIABLE_NAME, Variables.longValue(MockProvider.LONG_VARIABLE_VALUE));
          typedVariables.put(MockProvider.DOUBLE_VARIABLE_NAME, Variables.doubleValue(MockProvider.DOUBLE_VARIABLE_VALUE));
          typedVariables.put(MockProvider.STRING_VARIABLE_NAME, Variables.stringValue(MockProvider.STRING_VARIABLE_VALUE));
          typedVariables.put(MockProvider.DATE_VARIABLE_NAME, Variables.dateValue(MockProvider.DATE_VARIABLE_VALUE));
          typedVariables.put(MockProvider.BYTES_VARIABLE_NAME, Variables.byteArrayValue(MockProvider.BYTES_VARIABLE_VALUE));
          typedVariables.put(MockProvider.NULL_VARIABLE_NAME, Variables.untypedNullValue());
          externalTask.setAllVariablesTyped(typedVariables);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    assertCompleteRequestSerialization(objectMapper);
  }

  @Test
  public void shouldSetSingleVariableUntypedAccordingToCompleteRequest() throws Exception {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          externalTask.setVariable(MockProvider.BOOLEAN_VARIABLE_NAME, MockProvider.BOOLEAN_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.SHORT_VARIABLE_NAME, MockProvider.SHORT_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.INTEGER_VARIABLE_NAME, MockProvider.INTEGER_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.LONG_VARIABLE_NAME, MockProvider.LONG_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.STRING_VARIABLE_NAME, MockProvider.STRING_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.DOUBLE_VARIABLE_NAME, MockProvider.DOUBLE_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.DATE_VARIABLE_NAME, MockProvider.DATE_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.BYTES_VARIABLE_NAME, MockProvider.BYTES_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.NULL_VARIABLE_NAME, null);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    assertCompleteRequestSerialization(objectMapper);
  }

  @Test
  public void shouldSetSingleVariableTypedAccordingToCompleteRequest() throws Exception {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          externalTask.setVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME, Variables.booleanValue(MockProvider.BOOLEAN_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.SHORT_VARIABLE_NAME, Variables.shortValue(MockProvider.SHORT_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.INTEGER_VARIABLE_NAME, Variables.integerValue(MockProvider.INTEGER_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.LONG_VARIABLE_NAME, Variables.longValue(MockProvider.LONG_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.DOUBLE_VARIABLE_NAME, Variables.doubleValue(MockProvider.DOUBLE_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.STRING_VARIABLE_NAME, Variables.stringValue(MockProvider.STRING_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.DATE_VARIABLE_NAME, Variables.dateValue(MockProvider.DATE_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.BYTES_VARIABLE_NAME, Variables.byteArrayValue(MockProvider.BYTES_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.NULL_VARIABLE_NAME, Variables.untypedNullValue());

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    assertCompleteRequestSerialization(objectMapper);
  }
  
  /* tests if written variables can be read properly */

  @Test
  public void shouldSetSingleVariableUntypedAndGetSingleVariableTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          externalTask.setVariable(MockProvider.STRING_VARIABLE_NAME, MockProvider.STRING_VARIABLE_VALUE);

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    TypedValue typedValue = catchedExternalTask[0].getVariableTyped(MockProvider.STRING_VARIABLE_NAME);
    assertThat(typedValue.getType().getName(), is(MockProvider.STRING_VARIABLE_TYPE.toLowerCase()));
    assertThat(typedValue.getValue(), is(MockProvider.STRING_VARIABLE_VALUE));
  }

  @Test
  public void shouldSetSingleVariableUntypedAndGetAllVariablesTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          externalTask.setVariable(MockProvider.BOOLEAN_VARIABLE_NAME, MockProvider.BOOLEAN_VARIABLE_VALUE);
          externalTask.setVariable(MockProvider.BYTES_VARIABLE_NAME, MockProvider.BYTES_VARIABLE_VALUE);

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    VariableMap variableMap = catchedExternalTask[0].getAllVariablesTyped();
    assertThat(variableMap.size(), is(2));

    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getType().getName(), is(MockProvider.BOOLEAN_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getType().getName(), is(MockProvider.BYTES_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));
  }

  @Test
  public void shouldSetSingleVariableTypedAndGetSingleVariableTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          externalTask.setVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME, Variables.booleanValue(MockProvider.BOOLEAN_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.BYTES_VARIABLE_NAME, Variables.byteArrayValue(MockProvider.BYTES_VARIABLE_VALUE));

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = catchedExternalTask[0];

    assertThat(externalTask.getVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getType().getName(), is(MockProvider.BOOLEAN_VARIABLE_TYPE.toLowerCase()));
    assertThat(externalTask.getVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    assertThat(externalTask.getVariableTyped(MockProvider.BYTES_VARIABLE_NAME).getType().getName(), is(MockProvider.BYTES_VARIABLE_TYPE.toLowerCase()));
    assertThat(externalTask.getVariableTyped(MockProvider.BYTES_VARIABLE_NAME).getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));
  }

  @Test
  public void shouldSetSingleVariableTypedAndGetAllVariablesTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          externalTask.setVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME, Variables.booleanValue(MockProvider.BOOLEAN_VARIABLE_VALUE));
          externalTask.setVariableTyped(MockProvider.BYTES_VARIABLE_NAME, Variables.byteArrayValue(MockProvider.BYTES_VARIABLE_VALUE));

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    VariableMap variableMap = catchedExternalTask[0].getAllVariablesTyped();
    assertThat(variableMap.size(), is(2));

    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getType().getName(), is(MockProvider.BOOLEAN_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getType().getName(), is(MockProvider.BYTES_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));
  }

  @Test
  public void shouldSetAllVariablesUntypedAndGetAllVariablesTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, Object> variableMap = new HashMap<>();
          variableMap.put(MockProvider.BOOLEAN_VARIABLE_NAME, MockProvider.BOOLEAN_VARIABLE_VALUE);
          variableMap.put(MockProvider.BYTES_VARIABLE_NAME, MockProvider.BYTES_VARIABLE_VALUE);
          externalTask.setAllVariables(variableMap);

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    VariableMap variableMap = catchedExternalTask[0].getAllVariablesTyped();
    assertThat(variableMap.size(), is(2));

    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getType().getName(), is(MockProvider.BOOLEAN_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getType().getName(), is(MockProvider.BYTES_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));
  }

  @Test
  public void shouldSetAllVariablesTypedAndGetAllVariablesTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, TypedValue> variableMap = new HashMap<>();
          variableMap.put(MockProvider.BOOLEAN_VARIABLE_NAME, Variables.booleanValue(MockProvider.BOOLEAN_VARIABLE_VALUE));
          variableMap.put(MockProvider.BYTES_VARIABLE_NAME, Variables.byteArrayValue(MockProvider.BYTES_VARIABLE_VALUE));
          externalTask.setAllVariablesTyped(variableMap);

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    VariableMap variableMap = catchedExternalTask[0].getAllVariablesTyped();
    assertThat(variableMap.size(), is(2));

    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getType().getName(), is(MockProvider.BOOLEAN_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getType().getName(), is(MockProvider.BYTES_VARIABLE_TYPE.toLowerCase()));
    assertThat(variableMap.getValueTyped(MockProvider.BYTES_VARIABLE_NAME).getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));
  }

  @Test
  public void shouldSetAllVariablesTypedAndGetSingleVariableTyped() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, TypedValue> variableMap = new HashMap<>();
          variableMap.put(MockProvider.BOOLEAN_VARIABLE_NAME, Variables.booleanValue(MockProvider.BOOLEAN_VARIABLE_VALUE));
          variableMap.put(MockProvider.BYTES_VARIABLE_NAME, Variables.byteArrayValue(MockProvider.BYTES_VARIABLE_VALUE));
          externalTask.setAllVariablesTyped(variableMap);

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = catchedExternalTask[0];

    assertThat(externalTask.getVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getType().getName(), is(MockProvider.BOOLEAN_VARIABLE_TYPE.toLowerCase()));
    assertThat(externalTask.getVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME).getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    assertThat(externalTask.getVariableTyped(MockProvider.BYTES_VARIABLE_NAME).getType().getName(), is(MockProvider.BYTES_VARIABLE_TYPE.toLowerCase()));
    assertThat(externalTask.getVariableTyped(MockProvider.BYTES_VARIABLE_NAME).getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));
  }

  /* tests if overriding variables works properly */

  @Test
  public void shouldOverridePreviouslySetVariableBySetAllVariable() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, TypedValue> typedVariableMap = new HashMap<>();
          typedVariableMap.put("aVariableName", Variables.stringValue("aVariableValue"));
          externalTask.setAllVariablesTyped(typedVariableMap);

          Map<String, Object> untypedVariableMap = new HashMap<>();
          untypedVariableMap.put("aVariableName", 47L);
          externalTask.setAllVariables(untypedVariableMap);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = catchedExternalTask[0];

    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  @Test
  public void shouldOverridePreviouslySetVariableBySetVariable() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, TypedValue> typedVariableMap = new HashMap<>();
          typedVariableMap.put("aVariableName", Variables.stringValue("aVariableValue"));
          externalTask.setAllVariablesTyped(typedVariableMap);

          externalTask.setVariable("aVariableName", 47L);

          handlerInvoked.set(true);

          externalTaskService.complete(externalTask);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = catchedExternalTask[0];

    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  @Test
  public void shouldOverridePreviouslySetVariableBySetVariableTyped() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createExternalTaskWithoutVariables()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] catchedExternalTask = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {

          Map<String, TypedValue> typedVariableMap = new HashMap<>();
          typedVariableMap.put("aVariableName", Variables.stringValue("aVariableValue"));
          externalTask.setAllVariablesTyped(typedVariableMap);

          externalTask.setVariableTyped("aVariableName", Variables.longValue(47L));

          handlerInvoked.set(true);

          externalTaskService.complete(externalTask);

          catchedExternalTask[0] = externalTask;
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = catchedExternalTask[0];

    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  @Test
  public void shouldOverrideVariableRetrievedFromFetchAndLockBySetAllTyped() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();
    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();
    TypedValueDto typedValueDto = new TypedValueDto();
    typedValueDto.setValue("aVariableValue");
    typedValueDto.setType("String");
    variableMap.put("aVariableName", typedValueDto);

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          Map<String, TypedValue> untypedVariableMap = new HashMap<>();
          untypedVariableMap.put("aVariableName", Variables.longValue(47L));
          externalTask.setAllVariablesTyped(untypedVariableMap);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];

    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  @Test
  public void shouldOverrideVariableRetrievedFromFetchAndLockBySetAll() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();
    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();
    TypedValueDto typedValueDto = new TypedValueDto();
    typedValueDto.setValue("aVariableValue");
    typedValueDto.setType("String");
    variableMap.put("aVariableName", typedValueDto);

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          Map<String, Object> untypedVariableMap = new HashMap<>();
          untypedVariableMap.put("aVariableName", 47L);
          externalTask.setAllVariables(untypedVariableMap);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];

    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  @Test
  public void shouldOverrideVariableRetrievedFromFetchAndLockBySetSingleUntypedVariable() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();
    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();
    TypedValueDto typedValueDto = new TypedValueDto();
    typedValueDto.setValue("aVariableValue");
    typedValueDto.setType("String");
    variableMap.put("aVariableName", typedValueDto);

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          externalTask.setVariable("aVariableName", 47L);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];

    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  @Test
  public void shouldOverrideVariableRetrievedFromFetchAndLockBySetSingleTypedVariable() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();
    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();
    TypedValueDto typedValueDto = new TypedValueDto();
    typedValueDto.setValue("aVariableValue");
    typedValueDto.setType("String");
    variableMap.put("aVariableName", typedValueDto);

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          externalTask.setVariableTyped("aVariableName", Variables.longValue(47L));

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }

    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];
    assertVariableValue(externalTask, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);

    assertVariablePayloadOfCompleteRequest(objectMapper, "aVariableName", 47L, MockProvider.LONG_VARIABLE_TYPE);
  }

  /* tests if variables are merged correctly */

  @Test
  public void shouldMergeFetchedVariablesWithSetAllVariables() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();
    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();

    TypedValueDto aVariable = new TypedValueDto();
    aVariable.setValue("aVariableValue");
    aVariable.setType("String");
    variableMap.put("aVariableName", aVariable);

    TypedValueDto anotherVariable = new TypedValueDto();
    anotherVariable.setValue((short) 47);
    anotherVariable.setType("Short");
    variableMap.put("anotherVariableName", anotherVariable);

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          Map<String, Object> variables = new HashMap<>();
          variables.put("variableOne", true);
          variables.put("variableTwo", 4711);
          externalTask.setAllVariables(variables);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];
    assertVariableValue(externalTask, "aVariableName", "aVariableValue", MockProvider.STRING_VARIABLE_TYPE);
    assertVariableValue(externalTask, "anotherVariableName", (short) 47, MockProvider.SHORT_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableOne", true, MockProvider.BOOLEAN_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableTwo", 4711, MockProvider.INTEGER_VARIABLE_TYPE);

    Map<String, TypedValueDto> expectedDtoMap = new HashMap<>();

    TypedValueDto variableOneDto = createTypedValueDto(true, MockProvider.BOOLEAN_VARIABLE_TYPE);
    expectedDtoMap.put("variableOne", variableOneDto);

    TypedValueDto variableTwoDto = createTypedValueDto(4711, MockProvider.INTEGER_VARIABLE_TYPE);
    expectedDtoMap.put("variableTwo", variableTwoDto);

    assertVariablePayloadOfCompleteRequest(objectMapper, expectedDtoMap);
  }

  @Test
  public void shouldMergeFetchedVariablesWithSetAllVariablesTyped() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();
    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();

    TypedValueDto aVariable = createTypedValueDto("aVariableValue", "String");
    variableMap.put("aVariableName", aVariable);

    TypedValueDto anotherVariable = createTypedValueDto((short) 47, "Short");
    variableMap.put("anotherVariableName", anotherVariable);

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          Map<String, TypedValue> variables = new HashMap<>();
          variables.put("variableOne", Variables.booleanValue(true));
          variables.put("variableTwo", Variables.integerValue(4711));
          externalTask.setAllVariablesTyped(variables);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];
    assertVariableValue(externalTask, "aVariableName", "aVariableValue", MockProvider.STRING_VARIABLE_TYPE);
    assertVariableValue(externalTask, "anotherVariableName", (short) 47, MockProvider.SHORT_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableOne", true, MockProvider.BOOLEAN_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableTwo", 4711, MockProvider.INTEGER_VARIABLE_TYPE);

    Map<String, TypedValueDto> expectedDtoMap = new HashMap<>();

    TypedValueDto variableOneDto = createTypedValueDto(true, MockProvider.BOOLEAN_VARIABLE_TYPE);
    expectedDtoMap.put("variableOne", variableOneDto);

    TypedValueDto variableTwoDto = createTypedValueDto(4711, MockProvider.INTEGER_VARIABLE_TYPE);
    expectedDtoMap.put("variableTwo", variableTwoDto);

    assertVariablePayloadOfCompleteRequest(objectMapper, expectedDtoMap);
  }

  @Test
  public void shouldMergeAndOverrideFetchedVariablesWithSetAllVariables() throws Exception {
    // given
    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();

    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();
    variableMap.put("aVariableName", createTypedValueDto("aVariableValue", MockProvider.STRING_VARIABLE_TYPE));
    variableMap.put("anotherVariableName", createTypedValueDto((short) 47, MockProvider.SHORT_VARIABLE_TYPE));

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = {null};
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          Map<String, Object> variables = new HashMap<>();
          variables.put("aVariableName", 3.1415926535897932384626433);
          variables.put("variableOne", true);
          variables.put("variableTwo", 4711);
          externalTask.setAllVariables(variables);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];
    assertVariableValue(externalTask, "aVariableName", 3.1415926535897932384626433, MockProvider.DOUBLE_VARIABLE_TYPE);
    assertVariableValue(externalTask, "anotherVariableName", (short) 47, MockProvider.SHORT_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableOne", true, MockProvider.BOOLEAN_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableTwo", 4711, MockProvider.INTEGER_VARIABLE_TYPE);

    Map<String, TypedValueDto> typedValueDtoMap = new HashMap<>();
    typedValueDtoMap.put("variableOne", createTypedValueDto(true, MockProvider.BOOLEAN_VARIABLE_TYPE));
    typedValueDtoMap.put("variableTwo", createTypedValueDto(4711,  MockProvider.INTEGER_VARIABLE_TYPE));
    typedValueDtoMap.put("aVariableName", createTypedValueDto(3.1415926535897932384626433, MockProvider.DOUBLE_VARIABLE_TYPE));

    assertVariablePayloadOfCompleteRequest(objectMapper, typedValueDtoMap);
  }

  @Test
  public void shouldMergeAndOverrideFetchedVariablesWithSetSingleUntypedVariable() throws Exception {
    // given
    ExternalTaskImpl externalTaskResponse = (ExternalTaskImpl) MockProvider.createExternalTaskWithoutVariables();

    Map<String, TypedValueDto> variableMap = externalTaskResponse.getVariables();
    variableMap.put("aVariableName", createTypedValueDto("aVariableValue", MockProvider.STRING_VARIABLE_TYPE));
    variableMap.put("anotherVariableName", createTypedValueDto((short) 47, MockProvider.SHORT_VARIABLE_TYPE));

    mockFetchAndLockResponse(Collections.singletonList(externalTaskResponse));

    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final ExternalTask[] externalTaskReference = { null };
    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskReference[0] = externalTask;

          externalTask.setVariable("aVariableName", 3.1415926535897932384626433);
          externalTask.setVariable("variableOne", true);
          externalTask.setVariable("variableTwo", 4711);

          externalTaskService.complete(externalTask);

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    ExternalTask externalTask = externalTaskReference[0];
    assertVariableValue(externalTask, "aVariableName", 3.1415926535897932384626433, MockProvider.DOUBLE_VARIABLE_TYPE);
    assertVariableValue(externalTask, "anotherVariableName", (short) 47, MockProvider.SHORT_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableOne", true, MockProvider.BOOLEAN_VARIABLE_TYPE);
    assertVariableValue(externalTask, "variableTwo", 4711, MockProvider.INTEGER_VARIABLE_TYPE);

    Map<String, TypedValueDto> typedValueDtoMap = new HashMap<>();
    typedValueDtoMap.put("variableOne", createTypedValueDto(true, MockProvider.BOOLEAN_VARIABLE_TYPE));
    typedValueDtoMap.put("variableTwo", createTypedValueDto(4711,  MockProvider.INTEGER_VARIABLE_TYPE));
    typedValueDtoMap.put("aVariableName", createTypedValueDto(3.1415926535897932384626433, MockProvider.DOUBLE_VARIABLE_TYPE));

    assertVariablePayloadOfCompleteRequest(objectMapper, typedValueDtoMap);
  }

  /* tests if exceptions are thrown correctly */

  @Test
  public void shouldThrowUnsupportedTypeException() throws JsonProcessingException {
    // given
    mockFetchAndLockResponse(Collections.singletonList(MockProvider.createLockedTask()));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    final List<UnsupportedTypeException> exceptionReference = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          try {
            externalTask.setVariable("aVariableName", new ExternalTaskImpl());
          } catch (UnsupportedTypeException e) {
            exceptionReference.add(e);
          }

          try {
            externalTask.setVariableTyped("aVariableName", Variables.untypedValue(new ExternalTaskImpl()));
          } catch (UnsupportedTypeException e) {
            exceptionReference.add(e);
          }

          try {
            externalTask.setAllVariablesTyped(Collections.singletonMap("aVariableName", Variables.untypedValue(new ExternalTaskImpl())));
          } catch (UnsupportedTypeException e) {
            exceptionReference.add(e);
          }

          try {
            externalTask.setAllVariables(Collections.singletonMap("aVariableName", new ExternalTaskImpl()));
          } catch (UnsupportedTypeException e) {
            exceptionReference.add(e);
          }

          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();
    while (!handlerInvoked.get()) {
      // busy waiting
    }
    client.stop();

    // then
    assertThat(exceptionReference.get(0).getMessage(), containsString("no suitable mapper found for type ExternalTaskImpl"));
    assertThat(exceptionReference.get(1).getMessage(), containsString("no suitable mapper found for type ExternalTaskImpl"));
    assertThat(exceptionReference.get(2).getMessage(), containsString("no suitable mapper found for type ExternalTaskImpl"));
    assertThat(exceptionReference.get(3).getMessage(), containsString("no suitable mapper found for type ExternalTaskImpl"));
  }

  @Test
  public void shouldNotInvokeHandlerDueToTypeOfResponseAndValueDiffer() throws JsonProcessingException, InterruptedException {
    // given
    ExternalTask externalTask = MockProvider.createExternalTaskWithoutVariables();
    ((ExternalTaskImpl)externalTask).setVariables(Collections.singletonMap("aVariableName", createTypedValueDto("aWrongVariableValue", "Long")));
    mockFetchAndLockResponse(Collections.singletonList(externalTask));

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    ExternalTaskHandler externalTaskHandlerMock = mock(ExternalTaskHandler.class);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler(externalTaskHandlerMock);

    // when
    topicSubscriptionBuilder.open();
    Thread.sleep(1000);

    client.stop();

    // then
    verifyZeroInteractions(externalTaskHandlerMock);
  }

  // helper //////////////////////////////////

  private void assertVariableValue(ExternalTask externalTask, String variableName, Object variableValue, String variableType) {
    assertThat(externalTask.getVariableTyped(variableName).getType().getName(), is(variableType.toLowerCase()));
    assertThat(externalTask.getVariableTyped(variableName).getValue(), is(variableValue));

    assertThat(externalTask.getAllVariablesTyped().getValueTyped(variableName).getType().getName(), is(variableType.toLowerCase()));
    assertThat(externalTask.getAllVariablesTyped().getValueTyped(variableName).getValue(), is(variableValue));

    assertThat(externalTask.getVariable(variableName), is(variableValue));

    assertThat(externalTask.getAllVariables().get(variableName), is(variableValue));
  }

  protected TypedValueDto createTypedValueDto(Object variableValue, String variableType) {
    TypedValueDto typedValueDto = new TypedValueDto();
    typedValueDto.setValue(variableValue);
    typedValueDto.setType(variableType);

    return typedValueDto;
  }
  protected void mockFetchAndLockResponse(List<ExternalTask> externalTasks) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    byte[] externalTasksAsBytes = objectMapper.writeValueAsBytes(externalTasks);
    HttpEntity entity = new ByteArrayEntity(externalTasksAsBytes);
    doReturn(entity)
      .when(closeableHttpResponse).getEntity();
  }

  protected void assertAllVariablesUntyped(ExternalTask externalTask) {
    assertThat(externalTask.getAllVariables().size(), is(MockProvider.VARIABLES.size()));

    MockProvider.VARIABLES.forEach((variableName, variableValue) -> {
      assertNotNull(variableName);
      assertNotNull(variableValue);

      if (variableValue.getType().equals("Date")) {
        assertThat(externalTask.getAllVariables().get(variableName), is(MockProvider.DATE_VARIABLE_VALUE));
      } else {
        assertThat(externalTask.getAllVariables().get(variableName), is(variableValue.getValue()));
      }
    });
  }

  protected void assertAllVariablesTyped(ExternalTask externalTask) {
    assertThat(externalTask.getAllVariables().size(), is(MockProvider.VARIABLES.size()));

    MockProvider.VARIABLES.forEach((expectedVariableName, expectedVariableValue) -> {
      assertNotNull(expectedVariableName);
      assertNotNull(expectedVariableValue);

      TypedValue typedValue = externalTask.getAllVariablesTyped().getValueTyped(expectedVariableName);

      if (typedValue.getType().getName().equals("date")) {
        assertThat(typedValue.getValue(), is(MockProvider.DATE_VARIABLE_VALUE));
      } else {
        assertThat(typedValue.getType().getName(), is(expectedVariableValue.getType().toLowerCase()));
        assertThat(typedValue.getValue(), is(expectedVariableValue.getValue()));
      }
    });
  }

  protected void assertSingleVariableUntyped(ExternalTask externalTask) {
    boolean booleanValue = externalTask.getVariable(MockProvider.BOOLEAN_VARIABLE_NAME);
    assertThat(booleanValue, is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    short shortValue = externalTask.getVariable(MockProvider.SHORT_VARIABLE_NAME);
    assertThat(shortValue, is(MockProvider.SHORT_VARIABLE_VALUE));

    int integerValue = externalTask.getVariable(MockProvider.INTEGER_VARIABLE_NAME);
    assertThat(integerValue, is(MockProvider.INTEGER_VARIABLE_VALUE));

    long longValue = externalTask.getVariable(MockProvider.LONG_VARIABLE_NAME);
    assertThat(longValue, is(MockProvider.LONG_VARIABLE_VALUE));

    double doubleValue = externalTask.getVariable(MockProvider.DOUBLE_VARIABLE_NAME);
    assertThat(doubleValue, is(MockProvider.DOUBLE_VARIABLE_VALUE));

    String stringValue = externalTask.getVariable(MockProvider.STRING_VARIABLE_NAME);
    assertThat(stringValue, is(MockProvider.STRING_VARIABLE_VALUE));

    Date dateValue = externalTask.getVariable(MockProvider.DATE_VARIABLE_NAME);
    assertThat(dateValue, is(MockProvider.DATE_VARIABLE_VALUE));

    byte[] bytesValue = externalTask.getVariable(MockProvider.BYTES_VARIABLE_NAME);
    assertThat(bytesValue, is(MockProvider.BYTES_VARIABLE_VALUE));

    Object nullValue = externalTask.getVariable(MockProvider.NULL_VARIABLE_NAME);
    assertNull(nullValue);
  }

  protected void assertSingleVariableTyped(ExternalTask externalTask) {
    BooleanValue booleanValue = externalTask.getVariableTyped(MockProvider.BOOLEAN_VARIABLE_NAME);
    assertThat(booleanValue.getType(), is(PrimitiveValueType.BOOLEAN));
    assertThat(booleanValue.getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

    ShortValue shortValue = externalTask.getVariableTyped(MockProvider.SHORT_VARIABLE_NAME);
    assertThat(shortValue.getType(), is(PrimitiveValueType.SHORT));
    assertThat(shortValue.getValue(), is(MockProvider.SHORT_VARIABLE_VALUE));

    IntegerValue integerValue = externalTask.getVariableTyped(MockProvider.INTEGER_VARIABLE_NAME);
    assertThat(integerValue.getType(), is(PrimitiveValueType.INTEGER));
    assertThat(integerValue.getValue(), is(MockProvider.INTEGER_VARIABLE_VALUE));

    LongValue longValue = externalTask.getVariableTyped(MockProvider.LONG_VARIABLE_NAME);
    assertThat(longValue.getType(), is(PrimitiveValueType.LONG));
    assertThat(longValue.getValue(), is(MockProvider.LONG_VARIABLE_VALUE));

    DoubleValue doubleValue = externalTask.getVariableTyped(MockProvider.DOUBLE_VARIABLE_NAME);
    assertThat(doubleValue.getType(), is(PrimitiveValueType.DOUBLE));
    assertThat(doubleValue.getValue(), is(MockProvider.DOUBLE_VARIABLE_VALUE));

    StringValue stringValue = externalTask.getVariableTyped(MockProvider.STRING_VARIABLE_NAME);
    assertThat(stringValue.getType(), is(PrimitiveValueType.STRING));
    assertThat(stringValue.getValue(), is(MockProvider.STRING_VARIABLE_VALUE));

    DateValue dateValue = externalTask.getVariableTyped(MockProvider.DATE_VARIABLE_NAME);
    assertThat(dateValue.getType(), is(PrimitiveValueType.DATE));
    assertThat(dateValue.getValue(), is(MockProvider.DATE_VARIABLE_VALUE));

    BytesValue bytesValue = externalTask.getVariableTyped(MockProvider.BYTES_VARIABLE_NAME);
    assertThat(bytesValue.getType(), is(PrimitiveValueType.BYTES));
    assertThat(bytesValue.getValue(), is(MockProvider.BYTES_VARIABLE_VALUE));

    NullValueImpl nullValue = externalTask.getVariableTyped(MockProvider.NULL_VARIABLE_NAME);
    assertThat(nullValue.getType(), is(PrimitiveValueType.NULL));
    assertNull(nullValue.getValue());
  }

  protected void assertCompleteRequestSerialization(ObjectMapper objectMapper) throws JsonProcessingException {
    ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
    verify(objectMapper, atLeastOnce()).writeValueAsBytes(payloads.capture());

    boolean isAsserted = false;
    for (Object request : payloads.getAllValues()) {
      if (request instanceof CompleteRequestDto) {
        CompleteRequestDto completeRequestDto = (CompleteRequestDto) request;
        Map<String, TypedValueDto> typedValueDtoMap = completeRequestDto.getVariables();

        TypedValueDto booleanValueDto = typedValueDtoMap.get(MockProvider.BOOLEAN_VARIABLE_NAME);
        assertThat(booleanValueDto.getType(), is(MockProvider.BOOLEAN_VARIABLE_TYPE));
        assertThat(booleanValueDto.getValue(), is(MockProvider.BOOLEAN_VARIABLE_VALUE));

        TypedValueDto shortValueDto = typedValueDtoMap.get(MockProvider.SHORT_VARIABLE_NAME);
        assertThat(shortValueDto.getType(), is(MockProvider.SHORT_VARIABLE_TYPE));
        assertThat(shortValueDto.getValue(), is(MockProvider.SHORT_VARIABLE_VALUE));

        TypedValueDto integerValueDto = typedValueDtoMap.get(MockProvider.INTEGER_VARIABLE_NAME);
        assertThat(integerValueDto.getType(), is(MockProvider.INTEGER_VARIABLE_TYPE));
        assertThat(integerValueDto.getValue(), is(MockProvider.INTEGER_VARIABLE_VALUE));

        TypedValueDto longValueDto = typedValueDtoMap.get(MockProvider.LONG_VARIABLE_NAME);
        assertThat(longValueDto.getType(), is(MockProvider.LONG_VARIABLE_TYPE));
        assertThat(longValueDto.getValue(), is(MockProvider.LONG_VARIABLE_VALUE));

        TypedValueDto doubleValueDto = typedValueDtoMap.get(MockProvider.DOUBLE_VARIABLE_NAME);
        assertThat(doubleValueDto.getType(), is(MockProvider.DOUBLE_VARIABLE_TYPE));
        assertThat(doubleValueDto.getValue(), is(MockProvider.DOUBLE_VARIABLE_VALUE));

        TypedValueDto stringValueDto = typedValueDtoMap.get(MockProvider.STRING_VARIABLE_NAME);
        assertThat(stringValueDto.getType(), is(MockProvider.STRING_VARIABLE_TYPE));
        assertThat(stringValueDto.getValue(), is(MockProvider.STRING_VARIABLE_VALUE));

        TypedValueDto dateValueDto = typedValueDtoMap.get(MockProvider.DATE_VARIABLE_NAME);
        assertThat(dateValueDto.getType(), is(MockProvider.DATE_VARIABLE_TYPE));
        assertThat(dateValueDto.getValue(), is(MockProvider.DATE_VARIABLE_VALUE_SERIALIZED));

        TypedValueDto bytesValue = typedValueDtoMap.get(MockProvider.BYTES_VARIABLE_NAME);
        assertThat(bytesValue.getType(), is(MockProvider.BYTES_VARIABLE_TYPE));
        assertThat(bytesValue.getValue(), is(MockProvider.BYTES_VARIABLE_VALUE_SERIALIZED));

        TypedValueDto nullValueDto = typedValueDtoMap.get(MockProvider.NULL_VARIABLE_NAME);
        assertThat(nullValueDto.getType(), is(MockProvider.NULL_VARIABLE_TYPE));
        assertNull(nullValueDto.getValue());

        isAsserted = true;
      }
    }

    assertTrue(isAsserted);
  }

  protected void assertVariablePayloadOfCompleteRequest(ObjectMapper objectMapper, String variableName, Object variableValue, String variableType) throws JsonProcessingException {
    Map<String, TypedValueDto> expectedTypedValueMap = Collections.singletonMap(variableName, createTypedValueDto(variableValue, variableType));
    assertVariablePayloadOfCompleteRequest(objectMapper, expectedTypedValueMap);

  }

  protected void assertVariablePayloadOfCompleteRequest(ObjectMapper objectMapper, Map<String, TypedValueDto> expectedDtoMap) throws JsonProcessingException {
    ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
    verify(objectMapper, atLeastOnce()).writeValueAsBytes(payloads.capture());

    final Boolean[] isAsserted = {false};

    CompleteRequestDto completeRequestDto = (CompleteRequestDto) payloads.getAllValues().stream()
      .filter(payload -> payload instanceof CompleteRequestDto)
      .findFirst()
      .orElse(null);

    expectedDtoMap.forEach((variableName, typedValueDto) -> {
      Map<String, TypedValueDto> variableMap = completeRequestDto.getVariables();
      assertThat(variableMap.get(variableName).getType(), is(typedValueDto.getType()));
      assertThat(variableMap.get(variableName).getValue(), is(typedValueDto.getValue()));

      isAsserted[0] = true;
    });

    assertThat(expectedDtoMap.size(), is(completeRequestDto.getVariables().size()));

    assertTrue(isAsserted[0]);
  }

}
