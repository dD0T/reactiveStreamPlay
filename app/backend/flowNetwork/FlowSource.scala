package backend.flowNetwork

import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import scala.util.Random
import backend.flowTypes.Sentiment
import scala.concurrent.duration._

case class SetTarget(target: ActorRef)

class FlowSource(var target: ActorRef) extends Actor {
  import context.dispatcher

  private case object Tick

  val log = Logging(context.system, this)
  val tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)
  var uid: Long = 1

  override def postStop() = tick.cancel()

  def receive = {
    case SetTarget(t) =>
      log.info(s"Setting target $t")
      target = t

    case Tick =>
      target ! Sentiment(uid, 0, Random.nextDouble)
      uid += 1

  }
}
