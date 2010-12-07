akka-expect
=============
akka-expect is an [expect](http://expect.sf.net) mimic to facilitate easy testing of Akka actors. Following are a few examples, please see 
[ExpectActorSpec](https://github.com/joda/akka-expect/blob/master/src/test/scala/akka/extensions/expect/ExpectActorSpec.scala) for the full spec.

beforeEach
----------
In order to demonstrate the features of akka-expect we need the following two actors:
<pre><code>  class EchoService extends Actor {
    override def receive = {
      case msg => self reply msg
    }
  }

  class DiscardService extends Actor {
    override def receive = {
      case msg =>
    }
  }
</code></pre>
The expectActor is created using
<pre><code>val expectActor = actorOf[ExpectActor].start
</code></pre>
In order to avoid specifying the expectActor as a sender every time, we declare it implicit:
<pre><code>implicit val senderOption = Option(expectActor)
</code></pre>

expect
----------------
<pre><code>  "ExpectActor" should "enable synchronous assertion of specific messages" in {
    echoService ! "hello"
    expectActor ? "hello"
  }
</code></pre>

expectNo
----------------
<pre><code>  

  it should "enable explicit assertion of specific messages that are not expected" in {
    echoService ! "hola"
    expectActor expectNo "hello"
    expectActor expect "hola"

    // !? is shorthand for timeout
    echoService ! "hola"
    expectActor !? "hello"
    expectActor ? "hola"
  }

"ExpectActor" should "enable synchronous assertion of specific messages" in {
    echoService ! "hello"
    expectActor ? "hello"
  }
</code></pre>

expectNothing
-------------------
<pre><code>  it should "enable explicit assertion of no messages of any kind" in {
    discardService ! "hello"
    expectActor ? nothing
  }
</code></pre>

expectMultiple (ordered)
------------------------
<pre><code>  it should "enable assertion of multiple messages received in specific order" in {
    echoService ! "hello"
    echoService ! "world"
    expectActor ? inOrder("hello", "world")
  }
</code></pre>

expectMultiple (unordered)
--------------------------
<pre><code>  it should "enable assertion of multiple messages received in any order" in {
    echoService ! "hello"
    echoService ! "world"
    expectActor ? anyOrder("world", "hello")
  }
</code></pre>

expectAny
---------
<pre><code>  it should "enable assertion of messages of a specific class" in {
    echoService ! "hello"
    expectActor ?? classOf[String]
  }
</code></pre>

