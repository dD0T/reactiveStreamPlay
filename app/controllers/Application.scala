package controllers

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

  def add(nodetype: String, x: Int, y: Int) = Action.async {
    implicit val timeout = Timeout(500 millis)
    for {
      (id, _) <- sup ? CreateFlowObject(nodetype, x, y)
    } yield Ok(id.toString)
  }

  def connect(sourceId: Long, targetId: Long) = Action.async {
    implicit val timeout = Timeout(500 millis)
    for {
      ((_,_), connection: ActorRef) <- sup ? Connect(sourceId, targetId)
    } yield Ok(connection.path.toString) //FIXME: Not very useful ;)
  }

  def disconnect(sourceId: Long, targetId: Long) = Action.async {
    implicit val timeout = Timeout(500 millis)
    for {
      (_,_) <- sup ? Disconnect(sourceId, targetId)
    } yield Ok("done") //FIXME: Not very useful ;)
  }

  def postNode(id: Long) = Action(parse.json) { request =>
    def isLong(x:String) = try { x.toLong; true } catch { case _ => false }

    request.body.validate[Map[String,String]].asOpt match {
      case Some(config) =>
          sup ! {(id, backend.flowNetwork.Configuration(config))}
      case None => BadRequest(s"Invalid configuration in ${request.body}")

    }
    Ok("Update propagated")
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