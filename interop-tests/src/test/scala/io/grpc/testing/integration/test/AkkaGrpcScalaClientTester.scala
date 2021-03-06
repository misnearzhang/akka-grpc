/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package io.grpc.testing.integration.test

import java.io.InputStream

import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.google.protobuf.ByteString
import com.google.protobuf.empty.Empty
import io.grpc.testing.integration.messages._
import io.grpc.testing.integration2.{ ChannelBuilder, ClientTester, Settings }
import io.grpc.{ ManagedChannel, Status, StatusRuntimeException }
import org.junit.Assert._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }
import scala.util.Failure

class AkkaGrpcScalaClientTester(val settings: Settings)(implicit mat: Materializer, ex: ExecutionContext) extends ClientTester {

  private var client: TestServiceClient = null
  private var clientUnimplementedService: UnimplementedServiceClient = null

  private val awaitTimeout = 3.seconds

  def setUp(): Unit = {
    val grpcSettings = new GrpcClientSettings(
      settings.serverHost,
      settings.serverPort,
      Option(settings.serverHostOverride),
      certificate = Some("ca.pem"))
    client = TestServiceClient(grpcSettings)
    clientUnimplementedService = UnimplementedServiceClient(grpcSettings)
  }

  def tearDown(): Unit = {
    if (client != null) client.close()
  }

  def emptyUnary(): Unit = {
    assertEquals(Empty(), Await.result(client.emptyCall(Empty()), awaitTimeout))
  }

  def cacheableUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def largeUnary(): Unit = {
    val request =
      SimpleRequest(
        PayloadType.COMPRESSABLE,
        responseSize = 314159,
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](271828)))))

    val expectedResponse = SimpleResponse(payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](314159)))))

    val response = Await.result(client.unaryCall(request), awaitTimeout)
    assertEquals(expectedResponse, response)
  }

  def clientCompressedUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serverCompressedUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def clientStreaming(): Unit = {

    val requests = Seq(
      StreamingInputCallRequest(
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](27182))))),
      StreamingInputCallRequest(
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](8))))),
      StreamingInputCallRequest(
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](1828))))),
      StreamingInputCallRequest(
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](45904))))))

    val expected = StreamingInputCallResponse(aggregatedPayloadSize = 74922)

    val requestSrc = Source.fromIterator(() => requests.toIterator)
    val actual = Await.result(client.streamingInputCall(requestSrc), awaitTimeout)
    assertEquals(expected, actual)
  }

  def clientCompressedStreaming(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serverStreaming(): Unit = {

    val request =
      StreamingOutputCallRequest(
        responseType = PayloadType.COMPRESSABLE,
        responseParameters = Seq(
          ResponseParameters(31415),
          ResponseParameters(9),
          ResponseParameters(2653),
          ResponseParameters(58979)))

    val expected: Seq[StreamingOutputCallResponse] = Seq(
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](31415))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](9))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](2653))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](58979))))))

    val actual = Await.result(client.streamingOutputCall(request).runWith(Sink.seq), awaitTimeout)
    assertEquals(expected.size, actual.size)
    expected.zip(actual).foreach {
      case (exp, act) => assertEquals(exp, act)
    }
  }

  def serverCompressedStreaming(): Unit = {
    val request =
      StreamingOutputCallRequest(
        responseType = PayloadType.COMPRESSABLE,
        responseParameters = Seq(
          ResponseParameters(size = 31415, compressed = Some(true)),
          ResponseParameters(size = 92653, compressed = Some(true))))

    val expected: Seq[StreamingOutputCallResponse] = Seq(
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](31415))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](92653))))))

    val actual = Await.result(client.streamingOutputCall(request).runWith(Sink.seq), awaitTimeout)
    assertEquals(expected.size, actual.size)
    expected.zip(actual).foreach {
      case (exp, act) => assertEquals(exp, act)
    }
  }

  def pingPong(): Unit = {

    val requests = Seq(
      StreamingOutputCallRequest(
        responseParameters = Seq(ResponseParameters(31415)),
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](27182))))),
      StreamingOutputCallRequest(
        responseParameters = Seq(ResponseParameters(9)),
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](8))))),
      StreamingOutputCallRequest(
        responseParameters = Seq(ResponseParameters(2653)),
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](1828))))),
      StreamingOutputCallRequest(
        responseParameters = Seq(ResponseParameters(58979)),
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](45904))))))

    val expectedResponses = Seq(
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](31415))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](9))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](2653))))),
      StreamingOutputCallResponse(
        Option(Payload(body = ByteString.copyFrom(new Array[Byte](58979))))))

    val requestSrc = Source.fromIterator(() => requests.toIterator)
    val actual = Await.result(client.fullDuplexCall(requestSrc).runWith(Sink.seq), awaitTimeout)

    assertEquals(expectedResponses.size, actual.size)
    expectedResponses.zip(actual).foreach {
      case (exp, act) => assertEquals(exp, act)
    }
  }

  def emptyStream(): Unit = {
    val req = Source.empty[StreamingOutputCallRequest]
    val res = client.fullDuplexCall(req)
    val actual = Await.result(res.runWith(Sink.seq), awaitTimeout)
    assertEquals(actual.size, 0)
  }

  def computeEngineCreds(serviceAccount: String, oauthScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serviceAccountCreds(jsonKey: String, credentialsStream: InputStream, authScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def jwtTokenCreds(serviceAccountJson: InputStream): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def oauth2AuthToken(jsonKey: String, credentialsStream: InputStream, authScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def perRpcCreds(jsonKey: String, credentialsStream: InputStream, oauthScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def customMetadata(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def statusCodeAndMessage(): Unit = {

    // Assert unary
    val errorMessage = "test status message"
    val echoStatus = EchoStatus(Status.UNKNOWN.getCode.value(), errorMessage)
    val req: SimpleRequest = SimpleRequest(
      responseStatus = Some(echoStatus))
    val eventualResponse = client.unaryCall(req)

    Await.ready(eventualResponse, awaitTimeout)
      .onComplete {
        // TODO: a client-side feature that relaunches StatusRuntimeException as GrpcServiceException (hide impl)
        case Failure(e: StatusRuntimeException) =>
          assertEquals(Status.UNKNOWN.getCode, e.getStatus.getCode)
          assertEquals(errorMessage, e.getStatus.getDescription)
        case _ => fail(s"Expected to fail with StatusRuntimeException")
      }

    // Assert streaming
    val streamingRequest = StreamingOutputCallRequest(responseStatus = Some(echoStatus))
    val requests = Source.single(streamingRequest)

    Await.ready(client.fullDuplexCall(requests).runWith(Sink.head), awaitTimeout)
      .onComplete {
        // TODO: a client-side feature that relaunches StatusRuntimeException as GrpcServiceException (hide impl)
        case Failure(e: StatusRuntimeException) =>
          assertEquals(Status.UNKNOWN.getCode, e.getStatus.getCode)
          assertEquals(errorMessage, e.getStatus.getDescription)
        case x =>
          fail(s"Expected [GrpcServiceException] but got ${x.getClass}")
      }
  }

  def unimplementedMethod(): Unit = {
    Await.ready(client.unimplementedCall(Empty()), awaitTimeout)
      .onComplete {
        // TODO: a client-side feature that relaunches StatusRuntimeException as GrpcServiceException (hide impl)
        case Failure(e: StatusRuntimeException) =>
          assertEquals(Status.UNIMPLEMENTED.getCode, e.getStatus.getCode)
        case _ => fail(s"Expected to fail with UNIMPLEMENTED")
      }
  }

  def unimplementedService(): Unit = {
    Await.ready(clientUnimplementedService.unimplementedCall(Empty()), awaitTimeout)
      .onComplete {
        // TODO: a client-side feature that relaunches StatusRuntimeException as GrpcServiceException (hide impl)
        case Failure(e: StatusRuntimeException) =>
          assertEquals(Status.UNIMPLEMENTED.getCode, e.getStatus.getCode)
        case _ => fail(s"Expected to fail with UNIMPLEMENTED")
      }
  }

  def cancelAfterBegin(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def cancelAfterFirstResponse(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def timeoutOnSleepingServer(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

}
