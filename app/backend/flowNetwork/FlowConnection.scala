package backend.flowNetwork

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import backend.flowTypes.FlowObject

import scala.concurrent.duration._

object FlowConnection {
  /** Create FlowConnection Prop
   * @param source Source of the connection
   * @param sourceId ID of the source
   * @param target Target of the connection
   * @param targetId ID of the target
   * @param attributes Free form user attributes to attach to include in configuration messages
   * @return Akka Props
   */
  def props(source: ActorRef, sourceId: Long, target: ActorRef, targetId: Long, attributes: Map[String, String]): Props
    = Props(new FlowConnection(source, sourceId, target, targetId, attributes))
}

/**
 * Actor representing a connection between two flow nodes.
 *
 * Forwards message from the given source to the target. Keeps track of
 * throughput and sends update to parent in regular intervals.
 *
 * @param source Source of the connection
 * @param sourceId ID of the source
 * @param target Target of the connection
 * @param targetId ID of the target
 * @param attributes Free form user attributes to attach to include in configuration messages
 */
class FlowConnection(val source: ActorRef, val sourceId: Long,
                     val target: ActorRef, val targetId: Long,
                     val attributes: Map[String, String]) extends Actor {

  val log = Logging(context.system, this)

  var messages: Long = 0
  var lastThroughput: Long = 0

  import context.dispatcher
  private case object Tick
  val tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def postStop() = tick.cancel()

  def config = Configuration(attributes ++ Map(
    "actor" -> self.path.toString(),
    "sourceId" -> sourceId.toString,
    "targetId" -> targetId.toString,
    "throughput" -> (lastThroughput.toString + " m/s"),
    "display" -> "throughput"
  ))

  def receive = {
    case GetConfiguration =>
      sender() ! config

    case o: FlowObject if sender() == source =>
      messages += 1
      target ! o

    case Tick =>
      lastThroughput = messages
      messages = 0
      context.parent ! config

    case Shutdown =>
      log.info("Asked to shutdown")
      context.parent ! Stopping
      context.stop(self)
  }
}
