package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Writes._

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
  val linksRetrieverModes = Map("hackernews" -> HackerNewsRetriever, "reddit"->RedditRetriever)

  def index = Action { (request) =>
    Ok(views.html.index(imageExtractorModes, linksRetrieverModes))
  }

  def get(format:String) = Action { (request) =>
    val linksRetriever:LinksRetriever = request.queryString.get("service").flatMap(_.headOption).
      flatMap(linksRetrieverModes.get(_)).getOrElse(HackerNewsRetriever)
    val imageExtractor:ImageExtractor = request.queryString.get("image"  ).flatMap(_.headOption).
      flatMap(imageExtractorModes.get(_)).getOrElse(ScreenshotExtractor)
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
    def writes(o: LinkWithImage) = JsObject(Map(
      "url" -> JsString(o.link.url),
      "weight" -> JsNumber(o.link.weight),
      "title" -> JsString(o.link.title),
      "feedbackLink" -> JsString(o.link.feedbackLink),
      "feedbackText" -> JsString(o.link.feedbackText),
      "image" -> JsString(o.image.url)
    ))
  }

  def sequencePromises[A](list: List[Promise[A]]): Promise[List[A]] = {
    list.foldLeft(Promise.pure(List[A]()))((s,p) => s.flatMap( s => p.map(a => s :+ a))) 
  }

  def getResult(linksRetriever:LinksRetriever, imageExtractor:ImageExtractor):Promise[List[LinkWithImage]] = {
    val links = linksRetriever.getLinks()
    links.flatMap(links => {
      Logger.debug(links.length+" links found.");
      val images = sequencePromises(links.map(link => imageExtractor.getImage(link.url).map( (link, _) )) ).map(_.flatMap(_ match {
        case (link, Some(img)) => Some(LinkWithImage(link, img))
        case _ => None
      }))
      images.map(images => Logger.debug(images.length+" images found."));
      images
    })
  }


}
