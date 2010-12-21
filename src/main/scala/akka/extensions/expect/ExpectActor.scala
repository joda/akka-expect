package akka.extensions.expect

import akka.actor._
import Actor._
import akka.dispatch._
import akka.AkkaException

import org.junit.Assert._

class UnexpectedMessageException(message: String) extends AkkaException(message)

object ExpectActor {
  def nothing = new ObjectUnexpectation(Nil)
  def inOrder(list: Any*) = MessageSeqExpectation(list.toList)
  def anyOrder(list: Any*) = MessageSetExpectation(list.toSet)
  def noneOf(list: Any*) = ObjectUnexpectation(list.toList)
  def noneOf(list: Class[_]*) = ClassUnexpectation(list.toList)
  def anyOf(list: Class[_]*) = AnyExpectation(list.toList)

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

  case class AnyExpectation(expectedClasses: Seq[Class[_]]) extends Expectation {
    def requiredElements = 1
    def assert(actual: Seq[_]) = {
      val actualClasses = actual.asInstanceOf[Seq[AnyRef]].map(o => o.getClass)
      assertEquals(expectedClasses, actualClasses)
    }
  }

  sealed trait Unexpectation {
    def assert(actual: Seq[_]): Unit
  }

  case class ObjectUnexpectation(expected: Seq[Any]) extends Unexpectation {
    def assert(actual: Seq[_]) = {
      val intersection = actual intersect expected
      if (!intersection.isEmpty) throw new UnexpectedMessageException("Got unexpected messages: " + intersection)
    }
  }

  case class ClassUnexpectation(expected: Seq[Class[_]]) extends Unexpectation {
    def assert(actual: Seq[_]) = {
      val intersection = actual.asInstanceOf[Seq[AnyRef]].map(o => o.getClass) intersect expected
      if (!intersection.isEmpty) throw new UnexpectedMessageException("Got unexpected messages: " + intersection)
    }
  }

  case class Reset()
  case class RespondWith(request: AnyRef, response: AnyRef)

  type Result = Option[Throwable]
  
  implicit def actorRefExpect(me: ActorRef) = new {

    val timeout = 500

    def ?(message: Any) = expect(message)
    def expect(message: Any) = doExpect(MessageSeqExpectation(message :: Nil))
    
    def ?(expectation: Expectation) = expect(expectation)
    def expect(expectation: Expectation) = doExpect(expectation)

    def ?(unexpectation: Unexpectation) = expect(unexpectation)
    def expect(unexpectation: Unexpectation) = expectNo(unexpectation)


    def ?*(messages: Seq[Any]) = expectMultiple(messages)
    def expectMultiple(messages: Seq[Any]) = doExpect(MessageSeqExpectation(messages))

    def ?*(messages: scala.collection.immutable.Set[_]) = expectMultiple(messages)
    def expectMultiple(messages: Set[_]) = doExpect(MessageSetExpectation(messages))
    
    def !??(cls: Class[_ <: AnyRef]) = expectNo(null.asInstanceOf[AnyRef])
    def !?(message: AnyRef) = expectNo(message)
    def expectNo(message: Any): Unit = expectNo(message :: Nil)
    def expectNo(messages: Seq[Any]): Unit = expectNo(ObjectUnexpectation(messages))
    def expectNo(unexpectation: Unexpectation): Unit = {
      Thread.sleep(timeout)
      val result = send2me(unexpectation)
      result.asInstanceOf[Result].foreach(throw _)
    }

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

    private def send2me(expectation: AnyRef) = me.sendRequestReply(expectation, timeout, me)
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
    case unexpectation: Unexpectation => unexpect(unexpectation)

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
          if (self.senderFuture.isDefined)
            self.senderFuture.get.completeWithResult(responses.head.response)
          else if (self.sender.isDefined)
            self.sender.get ! responses.head.response
            
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

  def unexpect(unexpectation: Unexpectation) = {
    expector = self.senderFuture.get.asInstanceOf[CompletableFuture[Any]]
    try {
      unexpectation.assert(mq)
      expector.completeWithResult(None)
    }
    catch {
      case t: Throwable => expector.completeWithResult(Option(t))
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
