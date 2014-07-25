package backend.flowNetwork

import akka.actor._
import backend.NextFlowUID
import backend.flowNetwork.sinks.{FlowAccumulator, FlowFrequency, FlowTrace, FlowCounter}
import backend.flowNetwork.sources.{FlowTwitterSource, FlowIpsumSource, FlowNumberSource}
import backend.flowNetwork.transformations._
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.{Concurrent, Enumerator}
import play.api.libs.json.JsValue
import views.html.helper.input

/** Registers an observer for node and connection configuration updates */
case class Register(observer: ActorRef)
case class Unregister(observer: ActorRef)

/** Triggers a complete re-discovery of the whole network configuration */
case object DetectConfiguration

/** Requests Some[ActorRef] of the connection between given source and target from Supervisor */
case class LookupConnection(sourceId: Long, targetId: Long)
/** Requests Some[ActorRef] of the node with the given id */
case class LookupObj(id: Long)
/** Requests Some[Long] ID of the node with the given ActorRef */
case class LookupId(obj: ActorRef)

/** Returns a list of possible flow objects */
case object GetFlowObjectTypes
case object GetFlowObjects
case class CreateFlowObject(what: String, x: Int, y: Int)
case class DeleteFlowObject(id: Long)

case object GetConnections
case class Connect(source: Long, target: Long, attribus: Map[String, String])
case class Disconnect(source: Long, target: Long)

case object Shutdown
case object Stopping

case object RequestEnumerator

/** Used for representation and update of the (key -> value) configuration in a Node/Connection */
case class Configuration(config: Map[String, String])
/** Requests configuration from a FlowNode/Connection */
case object GetConfiguration

object FlowSupervisor {
  def props(): Props = Props(new FlowSupervisor)
}

class FlowSupervisor extends Actor with ActorLogging {

  val ordinaryFlowObjects = Map[String, (Long, String, Int, Int) => Props](
    FlowNumberSource.nodeType -> FlowNumberSource.props,
    FlowCrossbar.nodeType -> FlowCrossbar.props,
    FlowFilter.nodeType -> FlowFilter.props,
    FlowTokenizer.nodeType -> FlowTokenizer.props,
    FlowFrequency.nodeType -> FlowFrequency.props,
    FlowSentiment.nodeType -> FlowSentiment.props,
    FlowAccumulator.nodeType -> FlowAccumulator.props,
    FlowIpsumSource.nodeType -> FlowIpsumSource.props,
    FlowCounter.nodeType -> FlowCounter.props,
    FlowTrace.nodeType -> FlowTrace.props,
    FlowTwitterSource.nodeType -> FlowTwitterSource.props,
    FlowMultiplier.nodeType -> FlowMultiplier.props,
    FlowStopwordFilter.nodeType -> FlowStopwordFilter.props
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

  var connectionObjToIds = scala.collection.mutable.Map.empty[ActorRef, (Long, Long)]
  var connectionIdsToObj = scala.collection.mutable.Map.empty[(Long, Long), ActorRef]

  var currentNodeConfigurations = scala.collection.mutable.Map.empty[Long, Configuration]
  var currentConnectionConfigurations = scala.collection.mutable.Map.empty[(Long, Long), Configuration]

  var translators =  scala.collection.mutable.Map.empty[Long, ActorRef]

  var observers = scala.collection.mutable.Set.empty[ActorRef]

  private def connectionsFor(id: Long): scala.collection.mutable.Map[(Long, Long), ActorRef] =
    connectionIdsToObj.filter { case ((sourceId, targetId),_) => (sourceId == id || targetId == id) }

  private def notifyObservers[T](message: T) = observers map { _ ! message }

  private def disconnect(sourceId: Long, targetId: Long): Boolean = {
    connectionIdsToObj.remove((sourceId, targetId)) match {
      case Some(connection) =>
        log.info(s"Disconnecting $sourceId and $targetId")

        flowIdToObject.get(sourceId) match {
          case Some(source) => source ! RemoveTarget(connection)
          case None => // Fine, already gone
        }

        currentConnectionConfigurations.remove((sourceId, targetId))
        connectionIdsToObj.remove((sourceId, targetId))

        notifyObservers((sourceId, targetId), None)
        connection ! Shutdown
        true

      case _ =>
        log.warning(s"Asked to disconnect $sourceId from $targetId but have no connection")
        false
    }
  }

  def receive = {
    case GetFlowObjectTypes => sender() ! ordinaryFlowObjects.keys.toList

    case RequestEnumerator =>
      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      val uid = NextFlowUID()
      log.info(s"Translator reservation $uid")

      val shutdownTranslator = () => {
        translators.get(uid) match {
          case Some(translator) =>
            log.info(s"Shutting down translator $uid")
            translators.remove(uid)
            self ! Unregister(translator)
            translator ! Shutdown
          case None => log.info(s"Translator $uid already gone")
        }
      }

      val enumerator = Concurrent.unicast[JsValue](
        onStart = (channel) => {
          // Seems like someone actually
          log.info(s"Starting new translator for client session $uid")
          val translator = context.actorOf(MessageTranslator.props(channel), name = s"messageTranslator$uid")
          translators += uid -> translator
          self ! Register(translator)
          // Push current state
          currentNodeConfigurations map { case (k,v) => translator ! (k, v) }
          currentConnectionConfigurations map { case (k,v) => translator ! (k,v) }
        },
        onComplete = { shutdownTranslator() },
        onError = (_,_) => shutdownTranslator
      ).onDoneEnumerating(
          callback = shutdownTranslator
      )

      sender() ! enumerator

    case Register(observer) =>
      log.info(s"${observer} registered for updates")
      observers += observer

    case Unregister(observer) =>
      log.info(s"${observer} removed from updates")

    case DetectConfiguration =>
      log.info(s"${sender()} triggered configuration update")
      // Notify about current configuration state
      currentNodeConfigurations map { case (k,v) => notifyObservers(k, v) }
      currentConnectionConfigurations map { case (k,v) => notifyObservers(k,v) }

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
          sender() ! Some((id, obj))
        }
        case None =>
          log.warning(s"Asked to create unknown flow object type $objectType")
          None
      }

    case DeleteFlowObject(id: Long) =>
      flowIdToObject.get(id) match {
        case Some(obj) =>
          log.info(s"Deleting node $id")

          // First disconnect
          connectionsFor(id) map { case ((sourceId, targetId), _) => disconnect(sourceId, targetId)}
          // Then delete
          obj ! Shutdown

          flowIdToObject.remove(id)
          flowObjectToId.remove(obj)

          sender() ! Some(id) // Ack delete of ID
          currentNodeConfigurations.remove(id)
          notifyObservers(id, None)

        case None =>
          log.warning(s"Asked to delete unknown object $id")
          sender() ! None
      }

    case Configuration(data) =>
      flowObjectToId.get(sender()) match {
        case Some(id) =>
          currentNodeConfigurations(id) = Configuration(data)
          notifyObservers(id, Configuration(data))
        case None =>
          connectionObjToIds.get(sender()) match {
            case Some((sourceId, targetId)) =>
              currentConnectionConfigurations((sourceId, targetId)) = Configuration(data)
              notifyObservers((sourceId, targetId), Configuration(data))
            case None => log.error(s"Received configuration update for untracked actor ${sender()}")
          }
      }

    case (id: Long, Configuration(data)) =>
      // Forward config to addressed actor
      flowIdToObject.get(id) match {
        case Some(obj) =>
          obj ! Configuration(data)
          sender() ! true

        case None =>
          log.error(s"Asked to forward configuration for unknown id $id")
          sender ! false
      }

    case LookupConnection(sourceId, targetId) =>
      connectionIdsToObj.get((sourceId, targetId)) match {
        case Some(connection) => sender() ! Some(connection)
        case None => sender() ! None
      }

    case LookupObj(id) =>
      flowIdToObject.get(id) match {
        case Some(obj) => sender() ! Some(obj)
        case None => sender() ! None
      }

    case LookupId(obj) =>
      flowObjectToId.get(obj) match {
        case Some(id) => sender() ! Some(id)
        case None => sender() ! None
      }

    /**
     * Returns a set of sourceID, targetID of all connections
     */
    case GetConnections =>
      sender() ! connectionIdsToObj.keySet.toSet

    /**
     * Returns a set of all object IDs
     */
    case GetFlowObjects =>
      sender() ! flowIdToObject.keySet.toSet

    case Connect(sourceId, targetId, attributes) =>
      (flowIdToObject.get(sourceId), flowIdToObject.get(targetId)) match {
        case (Some(source), Some(target)) if !connectionIdsToObj.contains {
          (sourceId, targetId)
        } => {
          log.info(s"Creating new connection from $source to $target")
          val connection = context.actorOf(
            FlowConnection.props(source, sourceId, target, targetId, attributes),
            name = newActorName("FlowConnection"))

          connectionObjToIds += connection ->(sourceId, targetId)
          connectionIdsToObj += (sourceId, targetId) -> connection

          source ! AddTarget(connection)
          sender() ! Some(((sourceId, targetId), connection))
          connection ! GetConfiguration

        }
        case _ =>
          sender ! None
          log.warning(s"Asked to connect $sourceId with $targetId of which are invalid or already connected")
      }

    case Disconnect(sourceId, targetId) =>
      if (disconnect(sourceId, targetId)) {
        sender() ! Some((sourceId, targetId))
      } else {
        sender() ! None
      }
  }
}
