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

case class NewsSource(
  id: String,
  linksExtractor: LinksExtractor, 
  imageExtractor: ImageExtractor,
  title: String, 
  iconHtml: String
)

object Application extends Controller {
  
  val sourcesList = List(
    NewsSource(
      "hackernews",
      HackerNewsRetriever, 
      ScreenshotExtractor, 
      "HackerNews Exposé",
      """<a class="icon" style="background: #ff6600; color: white; border: 3px solid white; padding: 0px 6px; font-size: 0.8em; font-weight:
      bold; font-family: Arial, sans-serif;" href="http://news.ycombinator.com/news" target="_blank">Y</a>"""
    ),
    NewsSource(
      "reddit",
      RedditRootRetriever,
      ScreenshotExtractor,
      "Reddit Exposé",
      ""
    ),
    NewsSource(
      "googlenews",
      GoogleNewsRetriever,
      ScreenshotExtractor,
      "GoogleNews Exposé",
      ""
    ),
    NewsSource(
      "playframework",
      PlayFrameworkRssRetriever,
      ScreenshotExtractor,
      "PlayFramework Exposé",
      ""
    )
  )
  val sources = sourcesList map { s => (s.id, s) } toMap
  val defaultSource = sources("hackernews")

  def index = Action { (request) =>
    val source = request.queryString.get("source").flatMap(_.headOption).flatMap(sources.get(_)).getOrElse(defaultSource)
    Ok(views.html.index(source))
  }

  // TODO : this method has been tested to be slow, need a top cache
  def get(format:String) = Action { (request) =>
    val source = request.queryString.get("source").flatMap(_.headOption).flatMap(sources.get(_)).getOrElse(defaultSource)
    AsyncResult(
      getResult(source).extend(promise => {
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

  def getResult(source: NewsSource) : Promise[List[LinkWithImage]] = {
    val links = LinksFetcher.fetch(source.linksExtractor)
    links.flatMap(links => {
      Logger.debug(links.length+" links found.");
      val images = sequencePromises(links.map(link => 
          ImageFetcher.fetch(link.url)(source.imageExtractor).map( (link, _) )
        ) ).map(_.flatMap(_ match {
        case (link, Some(img)) => Some(LinkWithImage(link, img))
        case _ => None
      }))
      images.map(images => Logger.debug(images.length+" images found."));
      images
    })
  }


}
