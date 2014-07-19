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

  "FlowNode" should {
    "should offer gettable and settable properties" in new AkkaTestkitSpecs2Support {
      within(2 seconds) {
        val parent = system.actorOf(Props(new Actor {
          // Special construct for testing context.parent messages
          val child = context.actorOf(FlowNode.props(0, "node", "Testing", 100, 120, 1, 2), "flowNode")
          def receive = {
            case x if sender == child => testActor forward x
            case x => child forward x
          }
        }))

        val p = TestProbe()

        p.send(parent, GetConfiguration)
        var cfg = p.expectMsgType[Configuration].config
        cfg("id") must be equalTo("0")
        cfg("nodeType") must be equalTo("Testing")
        cfg("name") must be equalTo("node")
        cfg("x") must be equalTo("100")
        cfg("y") must be equalTo("120")
        cfg("inputs") must be equalTo("2")
        cfg("outputs") must be equalTo("1")

        p.send(parent, Configuration(Map(
          "name" -> "bernd",
          "x" -> "321",
          "y" -> "123",
          "id" -> "can't set this",
          "nodeType" -> "foo",
          "nonexistant" -> "thingy"
        )))

        // Should post update to parent
        cfg = expectMsgType[Configuration].config
        cfg("id") must be equalTo("0")
        cfg("nodeType") must be equalTo("Testing")
        cfg("name") must be equalTo("bernd")
        cfg("x") must be equalTo("321")
        cfg("y") must be equalTo("123")
      }
    }
  }

  "FlowSource" should {
    /* for every case where you would normally use "in", use 
       "in new AkkaTestkitSpecs2Support" to create a new 'context'. */
    "send FlowObjects to the registered target in expected intervals" in new AkkaTestkitSpecs2Support {
      within(2.1 second) {
        val source = system.actorOf(FlowSource.props(0, "source", 100, 122), "flowSource")
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
        val c = system.actorOf(Props(new Actor {
          // Special construct for testing context.parent messages
          val child = context.actorOf(FlowConnection.props(testActor, p.ref), "flowConnection")
          def receive = {
            case x if sender == child => testActor forward x
            case x => child forward x
          }
        }))

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
        val c = system.actorOf(FlowCrossbar.props(0, "cross", 1, 2), "FlowCrossbar")
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
      within(1.5 second) {
        val tok = system.actorOf(FlowTokenizer.props(0, "tok", 3, 4), "FlowTokenizer")

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
        tok ! Configuration(Map(
          "fieldOfInterest" -> "b")
        )

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
        val tok = system.actorOf(Props(new Actor {
          // Special construct for testing context.parent messages
          val child = context.actorOf(FlowTokenizer.props(0, "tok", 1, 4), "FlowTokenizer")
          def receive = {
            case x if sender == child => testActor forward x
            case x => child forward x
          }
        }))

        tok ! SetTarget(self)
        expectMsgType[Configuration].config("separator") must beEqualTo(" ")

        tok ! Configuration(Map("separator" -> "s"))
        expectMsgType[Configuration].config("separator") must beEqualTo("s")

        tok ! TestMessage(a = "asbsc")
        expectMsgType[WordObject].word must beEqualTo("a")
        expectMsgType[WordObject].word must beEqualTo("b")
        expectMsgType[WordObject].word must beEqualTo("c")

        tok ! Configuration(Map("separator" -> "(s|b)"))
        expectMsgType[Configuration].config("separator") must beEqualTo("(s|b)")

        tok ! TestMessage(a = "dsebf")
        expectMsgType[WordObject].word must beEqualTo("d")
        expectMsgType[WordObject].word must beEqualTo("e")
        expectMsgType[WordObject].word must beEqualTo("f")


      }
    }
  }

  "FlowSupervisor" should {
    "be able to create all simple flow types it supports" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val sup = system.actorOf(FlowSupervisor.props(), "FlowTokenizer")
        sup ! GetFlowObjectTypes
        expectMsgType[List[String]] foreach { name =>
          sup ! CreateFlowObject(name, 100, 200)
          expectMsgType[(Long, ActorRef)]
        }
      }
    }

    "correctly name objects" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val sup = system.actorOf(FlowSupervisor.props(), "FlowSupervisor")
        sup ! CreateFlowObject("FlowSource", 1, 2)
        expectMsgType[(Long, ActorRef)]._2.path.name must beEqualTo("FlowSource1")
        sup ! CreateFlowObject("FlowTokenizer", 3, 4)
        expectMsgType[(Long, ActorRef)]._2.path.name must beEqualTo("FlowTokenizer1")
        sup ! CreateFlowObject("FlowSource", 5, 6)
        expectMsgType[(Long, ActorRef)]._2.path.name must beEqualTo("FlowSource2")
      }
    }

    "be able to connect a source to a target" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        // Setup:  Source --> Accumulator --> Filter
        // The filter acts as a sink so the Accumulator becomes active

        val sup = system.actorOf(FlowSupervisor.props(), "FlowSupervisor")

        // Register for updates
        sup ! Register(self)

        // Create flow graph
        sup ! CreateFlowObject("FlowSource", 1, 2)
        val (sourceId, source) = expectMsgType[(Long, ActorRef)]

        expectMsgType[(Long, Configuration)] match {
          case (sourceId, Configuration(cfg)) if cfg("active") == "0" => //ok
          case f => failure(s"Unexpected: $f")
        }

        sup ! CreateFlowObject("FlowAccumulator", 3, 4)
        val (accId, acc) = expectMsgType[(Long, ActorRef)]

        expectMsgType[(Long, Configuration)] match {
          case (accId, Configuration(cfg)) if cfg("active") == "0" => //ok
          case f => failure(s"Unexpected: $f")
        }

        sup ! Connect(sourceId, accId)
        val ((_,_), c1) = expectMsgType[((Long, Long), ActorRef)]

        expectMsgType[(Long, Configuration)] match {
          case (sourceId, Configuration(cfg)) if cfg("active") == "1" => //ok
          case f => failure(s"Unexpected: $f")
        }

        sup ! CreateFlowObject("FlowFilter", 5, 6)
        val (filterId, filter) = expectMsgType[(Long, ActorRef)]

        expectMsgType[(Long, Configuration)] match {
          case (filterId, Configuration(cfg)) if cfg("active") == "0" => //ok
          case f => failure(s"Unexpected: $f")
        }

        sup ! Connect(accId, filterId)
        val ((_,_), c2) = expectMsgType[((Long, Long), ActorRef)]

        expectMsgType[(Long, Configuration)] match {
          case (accId, Configuration(cfg)) if cfg("active") == "1" => //ok
          case f => failure(s"Unexpected: $f")
        }
      }
    }
  }
}