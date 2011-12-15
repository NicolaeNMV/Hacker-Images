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


import akka.actor._
import akka.actor.Actor._
import play.api.libs.akka._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current
import sys.process._

object LocalScreenshot {
  val script = Play.getFile("phantomjs_scripts/render.js").getAbsolutePath
  val outputDir = {
    val f = Play.getFile("public/phantomjs_cache/")
    f.mkdirs()
    f.getAbsolutePath
  }
  val phantomjs = "phantomjs"

  def pathForUrl(url:String) = outputDir+"/"+md5(url)+".png"
  
  def urlToImage(url:String) : Option[Image] = {
    val output = pathForUrl(url)
    Process(phantomjs+" "+script+" "+url+" "+output).run().exitValue() match {
        case 0 => Some(Image(output))
        case _ => None
      }
  }

  def byteArrayToString(data: Array[Byte]) = {
     val hash = new java.math.BigInteger(1, data).toString(16)
     "0"*(32-hash.length) + hash
  }
  def md5(s: String):String = byteArrayToString(java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("US-ASCII")));
}

class LocalScreenshotActor extends Actor {
  def receive = {
    case url: String =>
      self reply LocalScreenshot.urlToImage(url)
  }
}

object LocalScreenshotExtractor extends ImageExtractor {
  val actor = actorOf[LocalScreenshotActor].start
  def getImage(pageUrl: String): Promise[Option[Image]] = {
    (actor ? pageUrl).mapTo[Option[Image]].asPromise
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

