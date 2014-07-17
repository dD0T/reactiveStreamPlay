package controllers

import backend.flowNetwork.{CreateFlowObject, FlowSupervisor, EventChannel}
import play.api._
import play.api.mvc._
import libs.EventSource
import play.api.libs.iteratee.Concurrent
import libs.json.JsValue
import play.libs.Akka

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

  def events = Action {
    Ok.feed(eventEnumerator
      .through(EventSource())
    ).as("text/event-stream")
  }

  def reset = Action {
    sup ! CreateFlowObject("FlowSource", 10, 10)
    sup ! CreateFlowObject("FlowAccumulator", 100, 100)
    Ok("reset")
  }
}