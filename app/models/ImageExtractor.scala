package models

import play.api._
import play.api.libs.concurrent._
import play.api.cache.BasicCache

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._
import java.net.URI

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
  val size = "1024x1024"
  def getImage(pageUrl:String): Promise[Option[Image]] = {
    Promise.pure(Some(Image("http://immediatenet.com/t/fs?Size="+size+"&URL="+pageUrl.replace("https://", "http://"))))
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

