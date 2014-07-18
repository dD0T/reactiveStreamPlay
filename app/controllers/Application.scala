package controllers

import akka.util.Timeout
import backend.flowNetwork.{CreateFlowObject, FlowSupervisor, EventChannel}
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

  def events = Action {
    Ok.feed(eventEnumerator through EventSource())
      .as("text/event-stream")
  }

  def reset = Action {
    //TODO: Implement this
    NotFound("Not implemented")
  }
}