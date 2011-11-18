package controllers

import play.api._
import play.api.mvc._

import models._

import org.jsoup.Jsoup
import org.jsoup.nodes._
import org.jsoup.select.Elements

import play.api.libs.concurrent._

case class LinkWithImage(url: String, weight: Double, image: String) 

object Application extends Controller {

  def index = Action {
    val links = HackerNewsRetriever.getLinks()
    AsyncResult(
      links.map(links => {
        Logger.debug(links.length+" links retrieved.");
        val images = links.flatMap(link => {
          val imageUrl = ScreenshotExtractor.getImageUrl(link.url)
          imageUrl.value match { // FIXME this is blocking... 
            case Redeemed(url) => url.map(
              imgurl => LinkWithImage(link.url, link.weight, imgurl)
            )
          }
        })
        Logger.debug(images.length+" images retrieved.");
        Logger.debug(""+images);
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
  
  def url2Image (url: String) = Action {
    AsyncResult({
      Logger.info("url2image url "+url)
      MostRelevantPageImageExtractor.getImageUrl(url).map(list => {
          list.map( list => {
            Ok(""+list)
          }).getOrElse(Ok("Not found"))
      })
  })
  }
  def urlImageAbsolutizeTest () = Action {
    MostRelevantPageImageExtractor.urlImageAbsolutizeTest()
    Ok("Working")
  }

}
