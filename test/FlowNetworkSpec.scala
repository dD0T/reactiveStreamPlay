import akka.util.Timeout
import backend.NextFlowUID
import backend.flowNetwork._
import backend.flowNetwork.SetTarget
import backend.flowNetwork.ThroughputUpdate
import backend.flowTypes.{WordObject, FlowObject, TwitterMessage, Sentiment}
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.pattern.ask
import akka.testkit._
import scala.concurrent.Await
import scala.concurrent.duration._

// Base structure taken from http://blog.xebia.com/2012/10/01/testing-akka-with-specs2/

/* A tiny class that can be used as a Specs2 'context'. */
abstract class AkkaTestkitSpecs2Support extends TestKit(ActorSystem())
  with After
  with ImplicitSender {
  // make sure we shut down the actor system after all tests have run
  def after = system.shutdown()
}

/* Both Akka and Specs2 add implicit conversions for adding time-related
   methods to Int. Mix in the Specs2 NoTimeConversions trait to avoid a clash. */
@RunWith(classOf[JUnitRunner])
class FlowNetworkSpec extends Specification with NoTimeConversions {
  sequential // forces all tests to be run sequentially

  "FlowSource" should {
    /* for every case where you would normally use "in", use 
       "in new AkkaTestkitSpecs2Support" to create a new 'context'. */
    "send FlowObjects to the registered target in expected intervals" in new AkkaTestkitSpecs2Support {
      within(2.1 second) {
        val source = system.actorOf(FlowSource.props(), "flowSource")
        source ! SetTarget(self)
        expectMsgType[Sentiment]
        val a = TestProbe()
        source ! SetTarget(a.ref)
        a.expectMsgType[Sentiment]
        expectNoMsg(0 seconds)
      }
    }
  }

  "FlowConnection" should {
    "forward FlowObjects and send throughput updates" in new AkkaTestkitSpecs2Support {
      within(1.1 second) {
        val p = TestProbe()
        val c = system.actorOf(FlowConnection.props(self, p.ref), "flowConnection")
        system.eventStream.subscribe(self, classOf[ThroughputUpdate])

        c ! "lulu"
        c ! TwitterMessage(0, "foo")
        c ! TwitterMessage(1, "bar")
        c ! "lulu"
        c ! Sentiment(2, 0, 4.5, 5.4)

        p.expectMsgType[TwitterMessage].message must be equalTo ("foo")
        p.expectMsgType[TwitterMessage].message must be equalTo ("bar")
        p.expectMsgType[Sentiment].positiveScore must be equalTo (4.5)

        expectMsgType[ThroughputUpdate] must be equalTo (ThroughputUpdate(3))
      }
    }
  }

  "FlowCrossbar" should {
    "forward and replicate received FlowObjects" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val c = system.actorOf(FlowCrossbar.props(), "FlowCrossbar")
        val a = TestProbe()
        val b = TestProbe()
        c ! AddTarget(a.ref)
        c ! AddTarget(b.ref)

        c ! "lulu"
        c ! TwitterMessage(0, "foo")

        c ! RemoveTarget(b.ref)
        c ! Sentiment(2, 0, 4.5, 5.4)
        c ! "lulu"

        a.expectMsgType[TwitterMessage].message must be equalTo("foo")
        b.expectMsgType[TwitterMessage].message must be equalTo("foo")

        a.expectMsgType[Sentiment].positiveScore must be equalTo (4.5)
        a.expectNoMsg(0 seconds)
        b.expectNoMsg(0 seconds)
      }
    }
  }

  "FlowTokenizer" should {

    case class TestMessage(override val uid: Long = NextFlowUID(), override val originUid: Long = NextFlowUID(), a: String = "a field", b: String = "b field") extends FlowObject {
      override def content(field: String): Option[Any] = field match {
        case "default" | "a" => Some(a)
        case "b" => Some(b)
        case _ => None
      }

      override def fields(): List[String] = List("default", "a", "b")
    }

    "tokenize incoming strings" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val tok = system.actorOf(FlowTokenizer.props(), "FlowTokenizer")

        tok ! TestMessage(a = "should be dropped")
        tok ! SetTarget(self)
        val M = TestMessage(a = "should tokenize this")
        tok ! M

        // Make sure we receive all word messages with valid origin and a seperate uid
        M.a.split(" ") foreach { (Word: String) => {
          expectMsgType[WordObject] match {
            case WordObject(uid, M.uid, Word) if uid != M.uid => // ok
            case o => failure(s"Expected $Word for $M, got $o")
          }
        }
        }

        // Test FOV targeting
        tok ! SetFieldOfInterest("b")

        val N = TestMessage(a = "not this", b = "but this")
        tok ! N

        N.b.split(" ") foreach { (Word: String) => {
          expectMsgType[WordObject] match {
            case WordObject(uid, N.uid, Word) if uid != N.uid => // ok
            case o => failure(s"Expected '$Word' for $N, got $o")
          }
        }
        }

        expectNoMsg(0 seconds)
      }
    }

    "accept arbitrary regex splitter" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val tok = system.actorOf(FlowTokenizer.props(), "FlowTokenizer")
        tok ! SetTarget(self)

        tok ! SetSeparator("s")
        tok ! TestMessage(a = "asbsc")
        expectMsgType[WordObject].word must beEqualTo("a")
        expectMsgType[WordObject].word must beEqualTo("b")
        expectMsgType[WordObject].word must beEqualTo("c")

        tok ! GetSeparator
        expectMsgType[Separator].separator must beEqualTo("s")

        tok ! SetSeparator("(s|b)")
        tok ! TestMessage(a = "dsebf")
        expectMsgType[WordObject].word must beEqualTo("d")
        expectMsgType[WordObject].word must beEqualTo("e")
        expectMsgType[WordObject].word must beEqualTo("f")

        tok ! GetSeparator
        expectMsgType[Separator].separator must beEqualTo("(s|b)")
      }
    }
  }

  "FlowSupervisor" should {
    "be able to create all simple flow types it supports" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val sup = system.actorOf(FlowSupervisor.props(), "FlowTokenizer")
        sup ! GetFlowObjectTypes
        expectMsgType[List[String]] foreach { name =>
          sup ! CreateFlowObject(name)
          expectMsgType[ActorRef]
        }
      }
    }

    "correctly name objects" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val sup = system.actorOf(FlowSupervisor.props(), "FlowSupervisor")
        sup ! CreateFlowObject("FlowSource")
        expectMsgType[ActorRef].path.name must beEqualTo("FlowSource1")
        sup ! CreateFlowObject("FlowTokenizer")
        expectMsgType[ActorRef].path.name must beEqualTo("FlowTokenizer1")
        sup ! CreateFlowObject("FlowSource")
        expectMsgType[ActorRef].path.name must beEqualTo("FlowSource2")
      }
    }
  }
}