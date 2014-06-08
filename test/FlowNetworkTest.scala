import backend.flowNetwork.{ThroughputUpdate, FlowConnection, SetTarget, FlowSource}
import backend.flowTypes.{TwitterMessage, Sentiment}
import org.specs2.mutable._
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
class ExampleSpec extends Specification with NoTimeConversions {
  sequential // forces all tests to be run sequentially

  "FlowSource" should {
    /* for every case where you would normally use "in", use 
       "in new AkkaTestkitSpecs2Support" to create a new 'context'. */
    "create FlowObject and send them to a registered target" in new AkkaTestkitSpecs2Support {
      within(1 second) {
        val source = system.actorOf(Props[FlowSource], "flowSource")
        source ! SetTarget(self)
        expectMsgType[Sentiment]
      }
    }

    "create FlowConnection and see whether it works" in new AkkaTestkitSpecs2Support {
      within(2 second) {
        val c = system.actorOf(FlowConnection.props(self), "flowConnection")
        system.eventStream.subscribe(self, classOf[ThroughputUpdate])

        c ! "lulu"
        c ! TwitterMessage(0, "foo")
        c ! TwitterMessage(1, "bar")
        c ! "lulu"
        c ! Sentiment(2, 0, 3.4)

        expectMsgType[TwitterMessage].message must be equalTo("foo")
        expectMsgType[TwitterMessage].message must be equalTo("bar")
        expectMsgType[ThroughputUpdate] must be equalTo (ThroughputUpdate(3))
        expectMsgType[Sentiment].sentiment must be equalTo(3.4)
        expectNoMsg()
      }
    }
  }
}