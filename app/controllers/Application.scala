package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {

  def overview = Action {
    implicit request => Ok(views.html.overview())
  }

  def flow = Action {
    implicit request => Ok(views.html.flow())
  }

}