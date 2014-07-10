package backend.flowNetwork

import akka.actor.{ActorRef, Actor, Props}
import akka.event.Logging
import backend.flowTypes.FlowObject
import scala.concurrent.duration._

case class ThroughputUpdate(val messagesPerSecond: Long)

object FlowConnection {
  def props(source: ActorRef, target: ActorRef): Props = Props(new FlowConnection(source, target))
}

class FlowConnection(val source: ActorRef, val target: ActorRef) extends Actor {
  import context.dispatcher

  private case object Tick

  val log = Logging(context.system, this)
  var messages: Long = 0
  val tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def postStop() = tick.cancel()

  def receive = {
    case o: FlowObject if sender() == source =>
      log.debug(s"Forwarding $o from $source to $target")
      messages += 1
      target ! o

    case Tick =>
      context.system.eventStream.publish(ThroughputUpdate(messages))
      messages = 0
  }
}
