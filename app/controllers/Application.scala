package controllers

import play.api._
import play.api.mvc._

import models._

import play.api.libs.concurrent._

object Application extends Controller {
  
  def index = Action {
    val links = HackerNewsRetriever.getLinks()
    AsyncResult(
      links.map(links => {
        Logger.debug(links.length+" links retrieved.");
        val images = links.flatMap(link => {
          val imageUrl = MostRelevantPageImageExtractor.getImageUrl(link.url)
          imageUrl.value /* FIXME this is blocking... */ match {
            case Redeemed(url) => url.map(
              url=>link.copy(url=url)
            )
          }
        })
        Logger.debug(images.length+" images retrieved.");
        Ok(views.html.index(images))
      })
    )
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
