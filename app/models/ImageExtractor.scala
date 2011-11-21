package models

import play.api._
import play.api.libs.concurrent._

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._
import java.net.URI

trait ImageExtractor {
  def getImageUrl(pageUrl:String): Promise[Option[String]]
}

object ScreenshotExtractor extends ImageExtractor {
  def getImageUrl(pageUrl:String): Promise[Option[String]] = {
    Promise.pure(Some( "http://immediatenet.com/t/fs?Size=1024x1024&URL="+pageUrl) )
  }
}

object MostRelevantPageImageExtractor extends ImageExtractor {
  def getImageUrl(url:String): Promise[Option[String]] = {
    Logger.debug("MostRelevantPageImageExtractor.getImageUrl("+url+")")
    WS.url(url).get().map(html => {
      val doc = Jsoup.parse(html.getResponseBody())
      val images = doc.select("img").headOption.map( (image) => {
        ImageUrlAbsolute.get(url,image.attr("src"))
      });
      Some(null)
    })
  }
  object ImageUrlAbsolute {
    def get(url: String, image: String):Option[String] = {
      Some(new URI(url).resolve(image).toString())
    }
  }

}

