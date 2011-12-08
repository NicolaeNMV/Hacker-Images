package controllers

import play.api._
import play.api.mvc._
import play.api.json._
import play.api.json.Writes._

import models._

import org.jsoup.Jsoup
import org.jsoup.nodes._
import org.jsoup.select.Elements

import play.api.libs.concurrent._

import scala.util.parsing.json._

/**
 * Link and Image joined class
 */
case class LinkWithImage(link: Link, image: Image)

object Application extends Controller {

  val imageExtractorModes = Map("screenshot" -> ScreenshotExtractor) /*, "relevant" -> MostRelevantPageImageExtractor*/ // Disabled for now
  val linksRetrieverModes = Map("hackernews" -> HackerNewsRetriever)

  def index = Action { (request) =>
    Ok(views.html.index())
  }

  def get(format:String) = Action { (request) =>
    val linksRetriever:LinksRetriever = request.queryString.get("service").flatMap(_.headOption).flatMap(linksRetrieverModes.get(_)).getOrElse(HackerNewsRetriever)
    val imageExtractor:ImageExtractor = request.queryString.get("image"  ).flatMap(_.headOption).flatMap(imageExtractorModes.get(_)).getOrElse(ScreenshotExtractor)
    AsyncResult(
      getResult(linksRetriever, imageExtractor).extend(promise => {
        promise.value match {
          case Redeemed(links) => format match {
            case "json" => Ok( toJson(links) )
            case _ => Status(415)("Format Not Supported")
          }
        }
      })
    )
  }

  implicit def linkWithImageWrites : Writes[LinkWithImage] = new Writes[LinkWithImage] {
    def writes(o: LinkWithImage):JsObject = {
      JsObject(Map(
      "url" -> JsString(o.link.url),
      "weight" -> JsNumber(o.link.weight),
      "title" -> JsString(o.link.title),
      "feedbackLink" -> JsString(o.link.feedbackLink),
      "feedbackText" -> JsString(o.link.feedbackText),
      "image" -> JsString(o.image.url)
    ))
    }
  }

  def getResult(linksRetriever:LinksRetriever, imageExtractor:ImageExtractor):Promise[List[LinkWithImage]] = {
    val links = linksRetriever.getLinks()
    links.map(links => {
      Logger.debug(links.length+" links found.");
      val images = links.flatMap(link => {
        val image = imageExtractor.getImage(link.url)
        image.value match { // FIXME this is blocking... 
          case Redeemed(url) => url.map(
            img => LinkWithImage(link, img)
          )
        }
      })
      Logger.debug(images.length+" images found.");
      images
    })
  }


}
