import backend.flowNetwork._
import backend.flowNetwork.SetTarget
import backend.flowNetwork.ThroughputUpdate
import backend.flowTypes.TwitterMessage
import backend.flowTypes.{TwitterMessage, Sentiment}
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit._
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
    "create FlowObject and send them to a registered target" in new AkkaTestkitSpecs2Support {
      within(2.1 second) {
        val source = system.actorOf(FlowSource.props(self), "flowSource")
        expectMsgType[Sentiment]
        val a = TestProbe()
        source ! SetTarget(a.ref)
        a.expectMsgType[Sentiment]
        expectNoMsg(0 seconds)
      }
    }
  }

  "FlowConnection" should {
    "forward and send throughput updates" in new AkkaTestkitSpecs2Support {
      within(1.1 second) {
        val p = TestProbe()
        val c = system.actorOf(FlowConnection.props(p.ref), "flowConnection")
        system.eventStream.subscribe(self, classOf[ThroughputUpdate])

        c ! "lulu"
        c ! TwitterMessage(0, "foo")
        c ! TwitterMessage(1, "bar")
        c ! "lulu"
        c ! Sentiment(2, 0, 3.4)

        p.expectMsgType[TwitterMessage].message must be equalTo ("foo")
        p.expectMsgType[TwitterMessage].message must be equalTo ("bar")
        p.expectMsgType[Sentiment].sentiment must be equalTo (3.4)

        expectMsgType[ThroughputUpdate] must be equalTo (ThroughputUpdate(3))
      }
    }
  }

  "FlowCrossbar" should {
    "forward and replicate" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val c = system.actorOf(FlowCrossbar.props(), "FlowCrossbar")
        val a = TestProbe()
        val b = TestProbe()
        c ! AddTarget(a.ref)
        c ! AddTarget(b.ref)

        c ! "lulu"
        c ! TwitterMessage(0, "foo")

        c ! RemoveTarget(b.ref)
        c ! Sentiment(2, 0, 3.4)
        c ! "lulu"

        a.expectMsgType[TwitterMessage].message must be equalTo("foo")
        b.expectMsgType[TwitterMessage].message must be equalTo("foo")

        a.expectMsgType[Sentiment].sentiment must be equalTo(3.4)
        a.expectNoMsg(0 seconds)
        b.expectNoMsg(0 seconds)
      }
    }
  }
}