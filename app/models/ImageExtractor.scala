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

/**
 * An ImageTransformer transforms a web page link to a single image.
 * Depending on the implementation, it can be a screenshot of the page, 
 * a real image taken from the page, or anything else.
 */
trait ImageExtractor {
  def getImage(pageUrl:String): Promise[Option[Image]]
}

case class Image(url: String)

/**
 * Screenshot implementation
 */
object ScreenshotExtractor extends ImageExtractor {
  val wsBase = Play.configuration.getString("ws.screenshot").getOrElse("http://localhost:8999")
  val format = "1024x1024"
  val logger = Logger("ScreenshotExtractor")

  private def encodeParameter(p:String) = URLEncoder.encode(p, "UTF-8")

  def getImage(pageUrl:String): Promise[Option[Image]] = {
    val url = wsBase+"/screenshot.jpg?url="+encodeParameter(pageUrl)+"&format="+format
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
  }
}

/**
 * Take the most relevant image from the page
 * FIXME: it's currently bad implemented
 */
object MostRelevantPageImageExtractor extends ImageExtractor {
  // TODO: move the cache to the controller side (top level)
  val cache = new BasicCache()
  val expirationSeconds = 5*60

  def getImage(url:String): Promise[Option[Image]] = {
    cache.get[Option[Image]](url).map(Promise.pure(_)).getOrElse({
      Logger.debug("MostRelevantPageImageExtractor.getImageUrl("+url+")")
      WS.url(url).get().map(html => {
        val src = Jsoup.parse(html.body).select("img").headOption.map(
          image => Image(new URI(url).resolve(image.attr("src")).toString())
        )
        cache.set(url, src)
        src
      })
    })
  }
}

