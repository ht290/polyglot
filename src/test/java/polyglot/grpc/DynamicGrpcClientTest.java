package polyglot.grpc;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import polyglot.test.TestProto;
import polyglot.test.TestProto.TestRequest;


/** Unit tests for {@link DynamicGrpcClient}. */
public class DynamicGrpcClientTest {
  private static final MethodDescriptor UNARY_METHOD = TestProto
      .getDescriptor()
      .findServiceByName("TestService")
      .findMethodByName("TestMethod");

  private static final MethodDescriptor SERVER_STREAMING_METHOD = TestProto
      .getDescriptor()
      .findServiceByName("TestService")
      .findMethodByName("TestMethodStream");

  private static final MethodDescriptor BIDI_STREAMING_METHOD = TestProto
      .getDescriptor()
      .findServiceByName("TestService")
      .findMethodByName("TestMethodBidi");

  private static final DynamicMessage REQUEST = DynamicMessage.newBuilder(
      TestRequest.newBuilder()
          .setMessage("some message")
          .build())
      .build();

  @Rule public MockitoRule mockitoJunitRule = MockitoJUnit.rule();
  @Mock private Channel mockChannel;
  @Mock private ListeningExecutorService mockExecutor;
  @Mock private StreamObserver<DynamicMessage> mockStreamObserver;

  private DynamicGrpcClient client;

  @Before
  public void setUp() {
  }

  @Test
  public void unaryMethodCall() {
    client = new DynamicGrpcClient(UNARY_METHOD, mockChannel, mockExecutor);
    client.call(REQUEST, mockStreamObserver);
  }
}
