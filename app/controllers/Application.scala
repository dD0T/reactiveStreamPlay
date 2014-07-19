package controllers

import java.util.NoSuchElementException

import akka.actor.ActorRef
import akka.util.Timeout
import backend.flowNetwork._
import play.api._
import akka.pattern.ask
import play.api.mvc._
import libs.EventSource
import play.api.libs.iteratee._
import play.libs.Akka
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import scala.concurrent.duration._

object Application extends Controller {
  val sup = Akka.system.actorOf(FlowSupervisor.props(), name = "flowSupervisor")
  val (eventEnumerator, eventChannel) = Concurrent.broadcast[JsValue]
  sup ! EventChannel(eventChannel)

  def overview = Action {
    implicit request => Ok(views.html.overview())
  }

  def flow = Action {
    implicit request => Ok(views.html.flow())
  }

  def postConnection = Action.async(parse.json) { request =>
    def isLong(x:String) = try { x.toLong; true } catch { case _ => false }

    request.body.validate[Map[String,String]].asOpt match {
      case Some(config) if ((config contains "sourceId") && (config contains "targetId")) =>
        if (!isLong(config("sourceId")) || !isLong(config("targetId"))) {
          Future.successful(BadRequest(s"Invalid initial configuration. sourceId and targetId must be strings convertible to Long"))
        } else {
          implicit val timeout = Timeout(500 millis)
          val (sourceId, targetId) = (config("sourceId").toLong, config("targetId").toLong)

          (for {
            ((_,_), connection: ActorRef) <- (sup ? Connect(sourceId, targetId, config))
              .mapTo[Option[((ActorRef, ActorRef), ActorRef)]].map(_.get)

          } yield Ok(connection.path.toString) //FIXME: Not very useful ;)
            ).recover {
            case _:NoSuchElementException => BadRequest(s"$sourceId and $targetId either already connected or invalid")
          }
        }
    }
  }

  def getConnections = Action.async {
    implicit val timeout = Timeout(100 millis)
    for {
      connections <- (sup ? GetConnections).mapTo[Set[(Long, Long)]]
    } yield Ok(Json.arr(connections.map {
      case (sourceId, targetId) => routes.Application.getConnection(sourceId, targetId).toString
    }))
  }

  def getConnection(sourceId: Long, targetId: Long) = Action.async {
    implicit val timeout = Timeout(100 millis)

    (for {
      connection <- (sup ? LookupConnection(sourceId, targetId)).mapTo[Option[ActorRef]].map(_.get)
      backend.flowNetwork.Configuration(data) <- (connection ? GetConfiguration)
    } yield Ok(Json.obj(
        "source" -> sourceId.toString,
        "target" -> targetId.toString,
        "config" -> Json.toJson(data)))
      ).recover {
      case _:NoSuchElementException => NotFound
    }
  }

  def deleteConnection(sourceId: Long, targetId: Long) = Action.async {
    implicit val timeout = Timeout(500 millis)
    (for {
      (_,_) <- (sup ? Disconnect(sourceId, targetId)).mapTo[Option[(Long,Long)]].map(_.get)
    } yield Ok("done")
    ).recover {
      case _:NoSuchElementException => NotFound
    }
  }

  /**
   * Creates a new node. Need nodeType, x and y to be present in the request.
   * @return Request + created id field
   */
  def postNode() = Action.async(parse.json) { request =>
    def isInt(x:String) = try { x.toInt; true } catch { case _ => false }

    request.body.validate[Map[String,String]].asOpt match {
      case Some(config) if config.keySet == Set("nodeType", "x", "y") =>
        if (!isInt(config("x")) || !isInt(config("y"))) {
            Future.successful(BadRequest(s"Invalid initial configuration. x and y must be Strings convertible to integer"))
        } else {
          // We received valid JSON with the required keys of the right shape
          // Send to backend for execution

          implicit val timeout = Timeout(500 millis)
          var (nodeType, x, y) = (config("nodeType"), config("x").toInt, config("y").toInt)

          (for {
            (id, node) <- (sup ? CreateFlowObject(nodeType, x, y)).mapTo[Option[(Long, ActorRef)]].map(_.get)
            backend.flowNetwork.Configuration(data) <- node ? GetConfiguration
          } yield Created(Json.obj(
              "id" -> id.toString,
              "config" -> Json.toJson(data)))
            .withHeaders("Location" -> routes.Application.getNode(id).toString)
          ).recover {
            case _:NoSuchElementException => BadRequest(s"Backend rejected update")
          }
        }
      case None => Future.successful(BadRequest(s"Invalid initial configuration. Must be json and contain only nodeType, x and y"))
    }
  }

  def getNodes = Action.async {
    implicit val timeout = Timeout(100 millis)
    for {
      nodes <- (sup ? GetFlowObjects).mapTo[Set[Long]]
    } yield Ok(Json.arr(nodes.map {
      id => routes.Application.getNode(id).toString
    }))
  }

  /**
   * GET node/:id
   * @param id Node ID
   * @return Node properties as JSON
   */
  def getNode(id: Long) = Action.async {
    implicit val timeout = Timeout(100 millis)

    (for {
      node <- (sup ? LookupObj(id)).mapTo[Option[ActorRef]].map(_.get)
      backend.flowNetwork.Configuration(data) <- (node ? GetConfiguration)
    } yield Ok(Json.obj(
      "id" -> id.toString,
      "config" -> Json.toJson(data)))
      ).recover {
      case _:NoSuchElementException => NotFound
    }
  }

  /**
   * PUT node/:id idempotent update function for node
   * @param id Node ID
   * @return NoContent
   */
  def putNode(id: Long) = Action(parse.json) { request =>
    def isLong(x:String) = try { x.toLong; true } catch { case _ => false }

    request.body.validate[Map[String,String]].asOpt match {
      case Some(config) =>
          sup ! {(id, backend.flowNetwork.Configuration(config))}
          NoContent // No waiting for backend
      case None =>
        BadRequest(s"Invalid configuration in ${request.body}")
    }
  }

  /**
   * DELETE node/:id deletion function for node
   * @param id Node ID
   * @return NoContent
   */
  def deleteNode(id: Long) = Action {
    sup ! DeleteFlowObject(id)
    NoContent // Not waiting for backend
  }

  def events = Action {
    Ok.feed(eventEnumerator through EventSource())
      .as("text/event-stream")
  }

  def reset = Action {
    sup ! DetectConfiguration
    Ok("Configuration detection started")
  }
}