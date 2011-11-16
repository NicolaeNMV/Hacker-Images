package controllers

import play.api._
import play.api.mvc._

import models._

import org.jsoup.Jsoup
import org.jsoup.nodes._
import org.jsoup.select.Elements

import collection.JavaConversions._ 

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index("Hello World"))
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
  
  def url2Image (url: String) = Action {
    Logger.info("url2image url "+url)
 }

}
