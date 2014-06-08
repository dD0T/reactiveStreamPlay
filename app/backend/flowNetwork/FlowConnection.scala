package backend.flowNetwork

import akka.actor.{ActorRef, Actor, Props}
import akka.event.Logging
import backend.flowTypes.FlowObject
import scala.concurrent.duration._

case class ThroughputUpdate(val messagesPerSecond: Long)

object FlowConnection {
  def props(target: ActorRef): Props = Props(new FlowConnection(target))
}

class FlowConnection(val target: ActorRef) extends Actor {
  import context.dispatcher

  private case object Tick

  val log = Logging(context.system, this)
  var messages: Long = 0
  val tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def postStop() = tick.cancel()

  def receive = {
    case o: FlowObject =>
      log.debug(s"Forwarding $o to $target")
      messages += 1
      target ! o

    case Tick =>
      context.system.eventStream.publish(ThroughputUpdate(messages))
      messages = 0
  }
}
