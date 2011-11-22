package models

import play.api._
import play.api.libs.concurrent.{Promise}
import play.api.cache.BasicCache

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._

case class Link(url: String, weight: Double, title: String)

trait LinksRetriever {
  def getLinks(): Promise[List[Link]]
}

object HackerNewsRetriever extends LinksRetriever {
  
  val url = "http://news.ycombinator.com/news"
  val cache = new BasicCache()
  val cacheKey = "models.HackerNewsRetriever.cacheKey"
  val expirationSeconds = 5

  def getLinks(): Promise[List[Link]] = {
    cache.get[List[Link]](cacheKey).map(Promise.pure(_)).getOrElse({
      WS.url(url).get().map(response => {
        Logger.debug(url+" getted.");
        val links = getLinksFromHtml(response.getResponseBody);
        cache.set(cacheKey, links, expirationSeconds)
        links
      })
    })
  }

  def getLinksFromHtml(html:String): List[Link] = {
    val doc = Jsoup.parse(html);
    val nodes = doc.select("td.title a[href^=http://]:not([href$=.pdf])");
    nodes.map(element => {
      val attr = element.attributes.find(_.getKey=="href").get
      val tr = element.parents().find(_.tag().getName()=="tr").get
      val rankStr = tr.children().head.text().trim().dropRight(1)
      val pointsStr = tr.nextElementSibling().select("td.subtext span").text().trim().takeWhile(_.isDigit)
      val rankPoints = if(rankStr.isEmpty) 0.0 else 2+nodes.length-rankStr.toDouble
      val points = if(pointsStr.isEmpty) 0.0 else pointsStr.toDouble
      val weight = rankPoints*rankPoints+points
      Link(attr.getValue, weight, element.text())
    }).toList.filter(_.weight>0.0).sortBy(-_.weight);
  }
}

/*
// some exception when trying to use this, Mmh, sounds like I found a bug?
object TestRetriever extends LinksRetriever {
  val datas = Range(1,7).toList.map(n => Link("http://localhost:9000/public/images/test/0"+n+".png", n) )
  def getLinks(): Promise[List[Link]] = Promise.pure(datas)
}
*/
