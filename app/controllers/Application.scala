package controllers

import play.api._
import play.api.mvc._

import models._

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def testHackerNews = Action {
    AsyncResult(
      HackerNewsRetriever.getLinks().map(list => {
        val res = list.map(link => {
          "<p>"+link.url+" => "+link.weight+"</p>"
        }).mkString("\n\r")
        Ok(res).withHeaders( ("Content-Type", "text/html") )
      })
    )
  }
  
}
