package controllers

import play.api._
import play.api.mvc._

import models._

import org.jsoup.Jsoup
import org.jsoup.nodes._
import org.jsoup.select.Elements

import play.api.libs.concurrent._

case class LinkWithImage(url: String, weight: Double, title: String, image: String) 

object Application extends Controller {

  val imageExtractorModes = Map("screenshot" -> ScreenshotExtractor
    /*, "relevant" -> MostRelevantPageImageExtractor*/ // Disabled for now
    )
  val linksRetrieverModes = Map("hackernews" -> HackerNewsRetriever)

  def index = Action { (request) =>
    Ok(views.html.index())
  }

  def getJson = Action { (request) =>
    val service = request.queryString.get("service").flatMap(_.headOption).getOrElse("hackernews")
    val image = request.queryString.get("image").flatMap(_.headOption).getOrElse("screenshot")
    imageExtractorModes.get(image).map(imageExtractor =>
      linksRetrieverModes.get(service).map( linksRetriever =>
          AsyncResult(
            getResult(linksRetriever, imageExtractor).extend(promise => {
              promise.value match {
                case Redeemed(links) => { 
                  import scala.util.parsing.json._
                  val json = new JSONArray(links.map(link => {
                    new JSONObject(Map( 
                      "url" -> link.url,
                      "weight" -> link.weight,
                      "title" -> link.title,
                      "image" -> link.image ))
                  }))
                  Ok(json.toString()).as("application/json")
                }
              }
            })
          )
      ).getOrElse(NotFound)
    ).getOrElse(NotFound)
  }

  def getResult(linksRetriever:LinksRetriever, imageExtractor:ImageExtractor):Promise[List[LinkWithImage]] = {
    val links = linksRetriever.getLinks()
    links.map(links => {
      Logger.debug(links.length+" links found.");
      val images = links.flatMap(link => {
        val imageUrl = imageExtractor.getImageUrl(link.url)
        imageUrl.value match { // FIXME this is blocking... 
          case Redeemed(url) => url.map(
            imgurl => LinkWithImage(link.url, link.weight, link.title, imgurl)
          )
        }
      })
      Logger.debug(images.length+" images found.");
      images
    })
  }


}
