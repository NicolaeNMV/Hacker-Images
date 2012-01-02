package models

import play.api._
import play.api.libs._
import play.api.libs.concurrent._
import play.api.cache.BasicCache
import play.api.Play.current

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._
import java.net._
import scala.util.matching._

/**
 * An ImageTransformer transforms a web page link to a single image.
 * Depending on the implementation, it can be a screenshot of the page, 
 * a real image taken from the page, or anything else.
 */
trait ImageExtractor {
  val cacheExpirationSeconds: Int
  def getImage(pageUrl: String): Promise[Option[Image]]
}

case class Image(url: String)

trait ImageOrElseExtractor extends ImageExtractor {
  val cacheExpirationSeconds: Int
  def getImage(pageUrl: String): Promise[Option[Image]] = {
    Promise.pure(
      try {
        if (new Regex("(gif|png|jpg|jpeg)$").findFirstIn(new URI(pageUrl).getPath).isDefined)
          Some(Image(pageUrl))
        else 
          None
      } catch {
        case _ => None
      }
    )
  }
}

/**
 * Screenshot implementation
 */
object ScreenshotExtractor extends ImageOrElseExtractor {
  val cacheExpirationSeconds = 10
  val wsBase = Play.configuration.getString("ws.screenshot").getOrElse("http://localhost:8999")
  val format = "1024x1024"
  val logger = Logger("ScreenshotExtractor")

  private def encodeParameter(p:String) = URLEncoder.encode(p, "UTF-8")

  override def getImage(pageUrl: String): Promise[Option[Image]] = {
    super.getImage(pageUrl).flatMap(o => if (o.isDefined) Promise.pure(o) else {
      val url = wsBase+"/screenshot.jpg?url="+encodeParameter(pageUrl)+"&size="+format
      WS.url(url).head().extend(_.value match {
        case Redeemed(response) =>
          response.status match {
            case 200 =>
              logger.debug("URL ready for "+url)
              Some(Image(url))

            case 202 =>
              logger.debug("URL processing for "+url)
              Some(Image(url))

            case code =>
              logger.debug("URL failed ("+code+") for "+url);
              None
          }
        case Thrown(e) =>
          logger.debug("URL failed ("+e+") for "+url)
          None
      })
    })
  }
}

/**
 * Take the most relevant image from the page
 * FIXME: it's currently bad implemented
 */
object MostRelevantPageImageExtractor extends ImageExtractor {
  val cacheExpirationSeconds = 5*60

  def getImage(url:String): Promise[Option[Image]] = {
    Logger.debug("MostRelevantPageImageExtractor.getImageUrl("+url+")")
    WS.url(url).get().map(html => {
      Jsoup.parse(html.body).select("img").headOption.map(
        image => Image(new URI(url).resolve(image.attr("src")).toString())
      )
    })
  }
}

