package backend.flowNetwork

import akka.actor.{Props, ActorRef, Actor}
import akka.event.Logging
import scala.util.Random
import backend.flowTypes.Sentiment
import scala.concurrent.duration._

object FlowSource {
  var nodeType = "FlowSource"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowSource(id, name, x, y))
}

class FlowSource(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowSource.nodeType, x, y, 1, 0) with TargetableFlow {

  import context.dispatcher

  private case object Tick

  val tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)
  var uid: Long = 0

  addConfigMapGetters(() => Map(
    "sourced" -> uid.toString,
    "display" -> "sourced"
  ))

  override def postStop() = tick.cancel()

  override def passive = {
    case Tick => // Nothing
  }
  override def active = {
    case Tick =>
      target ! Sentiment(uid, 0, Random.nextDouble, Random.nextDouble)
      uid += 1
      configUpdated()
  }
}
