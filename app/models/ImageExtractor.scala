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
    Promise.pure(None)
  }
}

object MostRelevantPageImageExtractor extends ImageExtractor {
  def getImageUrl(pageUrl:String): Promise[Option[String]] = {
    Promise.pure(None)
  }
  def urlImageAbsolutize(pageUrl: String, imagePath: String) = {
    // imagePath is already absolute
    if (imagePath.substring(0,"http://".length) == "http://")
      imagePath
    /**
     * pageUrl normalization
     */
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

    // image is relative
    if (imagePath.startsWith("..") )
       pageUrl + imagePath
    // image is absolute
    if (imagePath.startsWith("/")
      imagePath.substring(0,imagePath.indexOf("/")-1) + imagePath
  }
  def urlImageAbsolutizeTest(pageUrl: String, imagePath: String) = {
    val list = List(
      ("http://stackoverflow.com/questions/6070211/capturing-browser-specific-rendering-of-a-webpage","/posts/6070211/ivc/2d58"),
      ("http://stackoverflow.com/questions/6070211/capturing-browser-specific-rendering-of-a-webpage","../6070211/ivc/2d58")
    )
    val res = list.map( urlImageAbsolutize(_._1,_._2) )
    Logger.info(res)
  }



}

