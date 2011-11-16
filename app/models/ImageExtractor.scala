package models

import play.api._
import play.api.libs.concurrent._

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._

trait ImageExtractor {
  def getImageUrl(pageUrl:String): Promise[Option[String]]
}

object ScreenshotExtractor extends ImageExtractor {
  def getImageUrl(pageUrl:String): Promise[Option[String]] = {
    // Implement me
    Promise.pure(Some( "http://immediatenet.com/t/fs?Size=1024x768&URL="+pageUrl) )
  }
}

object MostRelevantPageImageExtractor extends ImageExtractor {
  def getImageUrl(url:String): Promise[Option[String]] = {
    Logger.info("ws get url "+url)
    WS.url(url).get().map(html => {
      Logger.info("ws inside ")

      val doc = Jsoup.parse(html.getResponseBody())
      val images = doc.select("img")
      
      if (images.length == 0)
        None
      else
        ImageUrlAbsolutizator.get(url, images.head.attr("src") )
    })
  }
  object ImageUrlAbsolutizator {
    def get(page: String, image: String):Option[String] = {
      var baseUrl = page
      var imagePath = image
      if (imagePath.substring(0,"http://".length) == "http://")
        Some(imagePath)
      else {
        Logger.info("Before normalize after")
        baseUrl = normalize(baseUrl)
        // imagePath is already absolute
        if (imagePath.substring(0,"http://".length) == "http://")
          Some(imagePath)
        // image is relative
        else if (imagePath.startsWith(".."))
          Some(baseUrl + imagePath)
        // image is absolute
        else if (imagePath.startsWith("/"))
          Some(baseUrl.substring(0,baseUrl.indexOf("/")-1) + imagePath)
        else {
          Logger.info("ImageUrlAbsolutizator not found baseUrl " + baseUrl + " imagePath " + imagePath + " find .. " + imagePath.startsWith(".."))
          None
        }
      }
    }
    def normalize(url: String) = {
      var pageUrl = url
      // remove pageUrl hash
      if (pageUrl.indexOf("#") != -1)
        pageUrl = pageUrl.substring(0,pageUrl.lastIndexOf("#")-1)
      // http://bla.com/bla -> bla.com/bla  
      if (pageUrl.startsWith("http://"))
        pageUrl = pageUrl.substring("http://".length,pageUrl.length-"http://".length)
      // bla.com/bla -> bla.com/
      if (pageUrl.indexOf("/") != -1 && !pageUrl.endsWith("/"))
        pageUrl = pageUrl.substring(0, pageUrl.lastIndexOf("/")-1 )
      pageUrl += "/"
      pageUrl
    }
  }
  def urlImageAbsolutize(page: String, image: String) = {
    ImageUrlAbsolutizator.get(page,image) 
  }
  def urlImageAbsolutizeTest() = {
    val list = List(
      ("http://stackoverflow.com/questions/6070211/capturing-browser-specific-rendering-of-a-webpage","/posts/6070211/ivc/2d58"),
      ("http://stackoverflow.com/questions/6070211/capturing-browser-specific-rendering-of-a-webpage","../6070211/ivc/2d58")
    )
    var res = list.map( test => urlImageAbsolutize(test._1,test._2) )
    res.foreach( img => Logger.info(""+img) ) 
  }



}

