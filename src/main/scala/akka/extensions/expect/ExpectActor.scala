package akka.extensions.expect

import akka.actor._
import Actor._
import akka.dispatch._
import akka.AkkaException

import org.junit.Assert._

class UnexpectedMessageException(message: String) extends AkkaException(message)

object ExpectActor {
  def nothing = new NoExpectation()

  sealed trait Expectation {
    def requiredElements: Int
    def assert(actual: Seq[_]): Unit
  }

  case class MessageSeqExpectation(expected: Seq[Any]) extends Expectation {
    def requiredElements = expected.size
    def assert(actual: Seq[_]) = {
      if (expected.head != null) 
        assertEquals(expected, actual)
    }
  }

  case class MessageSetExpectation(expected: Set[_]) extends Expectation {
    def requiredElements = expected.size
    def assert(actual: Seq[_]) = assertEquals(expected, actual.toSet)
  }

  case class AnyExpectation(expectedClass: Class[_]) extends Expectation {
    def requiredElements = 1
    def assert(actual: Seq[_]) = assertEquals(expectedClass, actual.head.asInstanceOf[AnyRef].getClass)
  }

  case class NoExpectation() extends Expectation {
    def requiredElements = 1
    def assert(actual: Seq[_]) = throw new UnexpectedMessageException("Got unexpected message: " + actual.head)
  }    

  case class Reset()
  case class RespondWith(request: AnyRef, response: AnyRef)

  type Result = Option[Throwable]
  
  implicit def actorRefExpect(me: ActorRef) = new {

    def ?(message: Any) = expect(message)
    def expect(message: Any) = doExpect(MessageSeqExpectation(message :: Nil))
    
    def ?*(messages: Seq[Any]) = expectMultiple(messages)
    def expectMultiple(messages: Seq[Any]) = doExpect(MessageSeqExpectation(messages))

    def ?*(messages: scala.collection.immutable.Set[_]) = expectMultiple(messages)
    def expectMultiple(messages: Set[_]) = doExpect(MessageSetExpectation(messages))
    
    def ??(expectedClass: Class[_]) = expectAny(expectedClass)
    def expectAny(expectedClass: Class[_]) = doExpect(AnyExpectation(expectedClass))

    def !??(cls: Class[_ <: AnyRef]) = expectNo(null)
    def !?(message: AnyRef) = expectNo(message)
    def expectNo(message: AnyRef) = {
      try {
        expect(message)
        throw new UnexpectedMessageException("Got unexpected message: " + message)
      } catch {
        case e: ActorTimeoutException => {}
        case t: Throwable => throw t
      }
    }

    def ?(expectation: NoExpectation) = expectNothing
    def expect(expectation: NoExpectation) = expectNothing
    def expectNothing = doNotExpect(new NoExpectation())
    
    private def doExpect(expectation: Expectation) = {
      val result = send2me(expectation)
      result.asInstanceOf[Result].foreach(throw _)
    }

    private def doNotExpect(expectation: Expectation) = {
      try {
        send2me(expectation)
      } catch {
        case e: ActorTimeoutException =>
      }
    }

    private def send2me(expectation: Expectation) = me.sendRequestReply(expectation, 500, me)
  }

}

class ExpectActor extends Actor {
  import ExpectActor._

  var mq: List[Any] = Nil
  
  var expectation: Expectation = null
  var expector: CompletableFuture[Any] = null
  var responses: List[RespondWith] = Nil

  def receive = {
    case expectation: Expectation => expect(expectation)

    case response: RespondWith =>
      responses = responses ++ List(response)
      self.senderFuture.foreach(_.completeWithResult(true))

    case Reset() =>
      mq = Nil
      responses = Nil
      self.senderFuture.foreach(_.completeWithResult(true))

    case message =>
      if (!responses.isEmpty) {
        if (message == responses.head.request) {
          self.senderFuture.get.completeWithResult(responses.head.response)
          responses = responses.tail
        }
      } else {
        mq = mq :+ message
      }
  }

  def expect(expectation: Expectation) = {
    this.expectation = expectation
    expector = self.senderFuture.get.asInstanceOf[CompletableFuture[Any]]
    if (mq.size < expectation.requiredElements) {
      become(expecting)
    } else {
      assertAndReply(expectation)
    }
  }

  def assertAndReply(expectation: Expectation) {
    try {
      expectation.assert(mq.take(expectation.requiredElements))
      mq = mq.drop(expectation.requiredElements)
      expector.completeWithResult(None)
    } catch {
      case t: Throwable => expector.completeWithResult(Option(t))
    }
  }

  def expecting: Receive = {
    case message =>
      mq = mq :+ message
      if (mq.size >= expectation.requiredElements) {
        assertAndReply(expectation)
        unbecome
      }
  }

}
