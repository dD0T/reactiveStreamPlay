package controllers

import play.api._
import play.api.mvc._
import libs.EventSource
import play.api.libs.iteratee.Concurrent
import libs.json.{Json, JsValue}

object Application extends Controller {

  val (eventEnumerator, eventChannel) = Concurrent.broadcast[JsValue]

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
    eventChannel.push(Json.obj("foo" -> 1))
    Ok("reset")
  }
}