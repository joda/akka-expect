package akka.extensions.expect

import ExpectActor._

import akka.actor._
import Actor._

import org.junit.runner._
import org.junit._

import org.scalatest.BeforeAndAfterEach
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner

// Utility actors to demonstrate the features of ExpectActor
class EchoService extends Actor {
  override def receive = {
    case msg => self reply msg
  }
}

class DiscardService extends Actor {
  override def receive = {
    case msg =>
  }
}

@RunWith(classOf[JUnitRunner])
class ExpectActorSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {
   
  "ExpectActor" should "enable synchronous assertion of specific messages" in {
    echoService ! "hello"
    expectActor expect "hello"

    // ? is shorthand for expect
    echoService ! "hello"
    expectActor ? "hello"
  }

  it should "raise a timeout exception if expected message is not received within the timeout period" in {
    // Expecting feedback from a DiscardService is silly, default timeout is 500ms
    discardService ! "hello"
    intercept[ActorTimeoutException] {
      expectActor ? "hello"
    }
  }

  it should "raise an assertion error when received message does not match expectation" in {
    // Trying to use an echo service as a translation service is very seldom successful
    val translationService = echoService

    translationService ! "hola"
    intercept[AssertionError] {
      expectActor ? "hello"
    }
  }

  it should "enable explicit assertion of specific messages that are not expected" in {
    pending
    // THINK: Maybe not eat messages in expectNo? Use discard to specifically eat messages?
    echoService ! "hola"
    expectActor expectNo "hello"

    // !? is shorthand for timeout
    echoService ! "hola"
    expectActor !? "hello"
  }

  it should "raise an error when receiving an unexpected message" in {
    echoService ! "hello"
    intercept[UnexpectedMessageException] {
      expectActor !? "hello"
    }
  }

  it should "enable explicit assertion of no messages of any kind" in {
    discardService ! "hello"
    expectActor.expectNothing

    discardService ! "hello"
    expectActor expect nothing

    discardService ! "hello"
    expectActor ? nothing
  }

  it should "enable assertion of multiple messages received in specific order (using Seq)" in {
    echoService ! "hello"
    echoService ! "world"
    expectActor expectMultiple List("hello", "world")

    // ?* is shorthand for expectMultiple
    echoService ! "hello"
    echoService ! "world"
    expectActor ?* List("hello", "world")
  }

  it should "raise an assertion error when expect multiple messages are received out of order" in {
    echoService ! "world"
    echoService ! "hello"
    intercept [AssertionError] {
      expectActor ?* List("hello", "world")
    }
  }

  it should "raise a timeout exception when expectMultiple(Seq) does not receive all expected messages" in {
    echoService ! "world"
    intercept [ActorTimeoutException] {
      expectActor ?* List("hello", "world")
    }
  }

  it should "enable assertion of multiple messages received, regardless of order (using Set)" in {
    echoService ! "hello"
    echoService ! "world"
    expectActor ?* Set("hello", "world")

    echoService ! "hello"
    echoService ! "world"
    expectActor ?* Set("world", "hello")
  }
  
  it should "raise a timeout exception when expectMultiple(Set) does not receive all expected messages" in {
    echoService ! "hello"
    intercept [ActorTimeoutException] {
      expectActor ?* Set("hello", "world")
    }
  }

  it should "enable assertion of messages of a specific class" in {
    echoService ! "hello"
    expectActor expectAny classOf[String]

    // ?? is shorthande for expectAny
    echoService ! "hello"
    expectActor ?? classOf[String]
  }

  it should "raise an assertion error when expectAny receives a message of wrong type" in {
    echoService ! 42
    intercept [AssertionError] {
      expectActor ?? classOf[String]
    }
  }

  var discardService: ActorRef = null
  var echoService: ActorRef = null
  var expectActor: ActorRef = null
  implicit var senderOption: Option[ActorRef] = None

  override def beforeEach {
    discardService = actorOf[DiscardService].start
    echoService = actorOf[EchoService].start
    expectActor = actorOf[ExpectActor].start
    senderOption = Option(expectActor)
  }

  override def afterEach {
    expectActor.stop
    echoService.stop
    discardService.stop
  }

}
