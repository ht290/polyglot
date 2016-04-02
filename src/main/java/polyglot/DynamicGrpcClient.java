package polyglot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

import com.google.auth.Credentials;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;

/** A grpc client which operates on dynamic messages. */
public class DynamicGrpcClient {
  private final MethodDescriptor protoMethodDescriptor;
  private final Channel channel;

  /** Creates a client for the supplied method, talking to the supplied endpoint. */
  public static DynamicGrpcClient create(
      MethodDescriptor protoMethod, HostAndPort endpoint, boolean useTls) {
    Channel channel = useTls ? createTlsChannel(endpoint) : createPlaintextChannel(endpoint);
    return new DynamicGrpcClient(protoMethod, channel);
  }

  /**
   * Creates a client for the supplied method, talking to the supplied endpoint. Passes the
   * supplied credentials on every rpc call.
   */
  public static DynamicGrpcClient createWithCredentials(
      MethodDescriptor protoMethod,
      HostAndPort endpoint,
      boolean useTls,
      Credentials credentials) {
    ExecutorService executor = Executors.newCachedThreadPool();
    Channel channel = useTls ? createTlsChannel(endpoint) : createPlaintextChannel(endpoint);
    return new DynamicGrpcClient(
        protoMethod,
        ClientInterceptors.intercept(channel, new ClientAuthInterceptor(credentials, executor)));
  }

  private DynamicGrpcClient(MethodDescriptor protoMethodDescriptor, Channel channel) {
    this.protoMethodDescriptor = protoMethodDescriptor;
    this.channel = channel;
  }

  /** Makes an rpc to the remote endpoint and returns the response. */
  public void call(DynamicMessage request, StreamObserver<DynamicMessage> streamObserver) {
    MethodType methodType = getMethodType();
    if (methodType == MethodType.UNARY) {

      System.out.println(">>>>> making unary call");


      ListenableFuture<DynamicMessage> response = ClientCalls.futureUnaryCall(
          channel.newCall(createGrpcMethodDescriptor(), CallOptions.DEFAULT),
          request);
      Futures.addCallback(response, new SingletonStreamCallback<DynamicMessage>(streamObserver));
    } else {

      System.out.println(">>>>> making streaming call");


      ClientCalls.asyncServerStreamingCall(
          channel.newCall(createGrpcMethodDescriptor(), CallOptions.DEFAULT),
          request,
          streamObserver);
    }
  }

  private io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> createGrpcMethodDescriptor() {
    return io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>create(
        getMethodType(),
        getFullMethodName(),
        new DynamicMessageMarshaller(protoMethodDescriptor.getInputType()),
        new DynamicMessageMarshaller(protoMethodDescriptor.getOutputType()));
  }

  private String getFullMethodName() {
    String serviceName = protoMethodDescriptor.getService().getFullName();
    String methodName = protoMethodDescriptor.getName();
    return io.grpc.MethodDescriptor.generateFullMethodName(serviceName, methodName);
  }

  /** Returns the appropriate method type based on whether the client or server expect streams. */
  private MethodType getMethodType() {
    boolean clientStreaming = protoMethodDescriptor.toProto().getClientStreaming();
    if (clientStreaming) {
      throw new UnsupportedOperationException("Requests with streaming clients not yet supported");
    }

    boolean serverStreaming = protoMethodDescriptor.toProto().getServerStreaming();
    if (serverStreaming) {
      return MethodType.SERVER_STREAMING;
    } else {
      return MethodType.UNARY;
    }
  }

  private static Channel createPlaintextChannel(HostAndPort endpoint) {
    return NettyChannelBuilder.forAddress(endpoint.getHostText(), endpoint.getPort())
        .negotiationType(NegotiationType.PLAINTEXT)
        .build();
  }

  private static Channel createTlsChannel(HostAndPort endpoint) {
    SslContext sslContext;
    try {
      sslContext = GrpcSslContexts.forClient().build();
    } catch (SSLException e) {
      throw new RuntimeException("Failed to create ssl context", e);
    }

    return NettyChannelBuilder.forAddress(endpoint.getHostText(), endpoint.getPort())
        .sslContext(sslContext)
        .negotiationType(NegotiationType.TLS)
        .build();
  }

  /**
   * A {@link FutureCallback} which provides an adapter from a future to a stream with a single
   * response.
   */
  private static class SingletonStreamCallback<T> implements FutureCallback<T> {
    private final StreamObserver<T> streamObserver;

    private SingletonStreamCallback(StreamObserver<T> streamObserver) {
      this.streamObserver = streamObserver;
    }

    @Override
    public void onFailure(Throwable t) {
      streamObserver.onError(t);
    }

    @Override
    public void onSuccess(T result) {
      streamObserver.onNext(result);
      streamObserver.onCompleted();
    }
  }
}
