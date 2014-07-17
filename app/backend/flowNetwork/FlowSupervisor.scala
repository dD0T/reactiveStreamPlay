package backend.flowNetwork

import akka.actor._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Concurrent.Channel
import backend.NextFlowUID

case class Register(observer: ActorRef)

case object DetectConfiguration

case class LookupId(id: Long)
case class LookupObj(obj: ActorRef)

case object GetFlowObjectTypes
case class CreateFlowObject(what: String, x: Int, y: Int)

case object GetConnections
case class Connect(source: Long, target: Long)
case class Disconnect(source: Long, target: Long)

case class EventChannel(chan: Channel[JsValue])

object FlowSupervisor {
  def props(): Props = Props(new FlowSupervisor)
}

class FlowSupervisor extends Actor with ActorLogging {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  val ordinaryFlowObjects = Map[String, (Long, String, Int, Int) => Props](
    "FlowSource" -> FlowSource.props,
    "FlowCrossbar" -> FlowCrossbar.props,
    "FlowFilter" -> FlowFilter.props,
    "FlowTokenizer" -> FlowTokenizer.props,
    "FlowFrequency" -> FlowFrequency.props,
    "FlowSentiment" -> FlowSentiment.props,
    "FlowAccumulator" -> FlowAccumulator.props
  )

  object newActorName {
    var objectCounts = scala.collection.mutable.Map[String, Int]().withDefaultValue(0)
    def apply(name: String) = {
      objectCounts(name) += 1
      s"$name${objectCounts(name)}"
    }
  }

  var flowIdToObject = scala.collection.mutable.Map.empty[Long, ActorRef]
  var flowObjectToId = scala.collection.mutable.Map.empty[ActorRef, Long]
  var connections = scala.collection.mutable.Map.empty[(ActorRef, ActorRef), ActorRef]
  var observers = scala.collection.mutable.Set.empty[ActorRef]

  def receive = {
    case GetFlowObjectTypes => sender() ! ordinaryFlowObjects.keys.toList

    case EventChannel(channel: Channel[JsValue]) =>
      log.info("Starting event channel translator")
      val translator = context.actorOf(MessageTranslator.props(channel), name = "messageTranslator")
      self ! Register(translator)

    case Register(observer) =>
      log.info(s"${observer} registered for updates")
      observers += observer

    case DetectConfiguration =>
      log.info(s"${sender()} triggered configuration detection")
      // Request all node configurations
      flowObjectToId foreach { case (obj, _) => obj ! GetConfiguration }
      //TODO: Should be able to provide connection information too

    case CreateFlowObject(objectType, x, y) =>
      ordinaryFlowObjects.get(objectType) match {
        case Some(_) => {
          log.info(s"Creating new $objectType for ${
            sender()
          }")
          val name = newActorName(objectType)
          val id = NextFlowUID()
          val obj = context.actorOf(ordinaryFlowObjects(objectType)(id, name, x, y), name = name)
          flowIdToObject += id -> obj
          flowObjectToId += obj -> id

          obj ! GetConfiguration // Pull configuration
          sender() ! (id, obj)
        }
        case None =>
          log.warning(s"Asked to create unknown flow object type $objectType")
      }

    case Configuration(data) =>
      flowObjectToId.get(sender()) match {
        case Some(id) => observers map { (o) => o ! (id, Configuration(data)) }
        case None => log.error(s"Received configuration update from untracked actor ${sender()}")
      }

    case (id: Long, Configuration(data)) =>
      // Forward config to addressed actor
      flowIdToObject.get(id) match {
        case Some(obj) => obj ! Configuration(data)
        case None => log.error(s"Asked to forward configuration for unknown id $id")
      }

    case LookupId(id) =>
      flowIdToObject.get(id) match {
        case Some(obj) => sender() ! obj
        case None => sender() ! null
      }

    case LookupObj(obj) =>
      flowObjectToId.get(obj) match {
        case Some(id) => sender() ! id
        case None => sender() ! null
      }

    case GetConnections =>
      sender() ! connections

    case Connect(sourceId, targetId) =>
      (flowIdToObject.get(sourceId), flowIdToObject.get(targetId)) match {
        case (Some(source), Some(target)) if !connections.contains {(source, target)} => {
          log.info(s"Creating new connection from $source to $target")
          val connection = context.actorOf(FlowConnection.props(source, target), name = newActorName("FlowConnection"))
          connections += (source, target) -> connection
          source ! AddTarget(connection)
          sender() ! ((sourceId, targetId), connection)
        }
        case _ => log.warning(s"Asked to connect $sourceId with $targetId of which are invalid or already connected")
      }

    case Disconnect(sourceId, targetId) =>
      (flowIdToObject.get(sourceId), flowIdToObject.get(targetId)) match {
        case (Some(source), Some(target)) => {
          connections.remove((source, target)) match {
            case Some(connection) =>
              log.info(s"Disconnecting $source and $target")
              source ! RemoveTarget(connection)
              connection ! Kill
            case _ => log.warning(s"Asked to disconnect $source from $target but have no connection")
          }
        }
        case _ => log.warning(s"Asked to connect $sourceId with $targetId of which at least one is unknown")
      }
  }
}
