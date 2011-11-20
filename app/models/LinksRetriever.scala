package models

import play.api._
import play.api.libs.concurrent.{Promise}

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._

case class Link(url: String, weight: Double, title: String)

trait LinksRetriever {
  def getLinks(): Promise[List[Link]]
}

object HackerNewsRetriever extends LinksRetriever {
  val url = "http://news.ycombinator.com/news";
  def getLinks(): Promise[List[Link]] = {
    WS.url(url).get().map(response => {
      Logger.debug(url+" getted.");
      val doc = Jsoup.parse(response.getResponseBody);
      val nodes = doc.select("td.title a[href^=http://]:not([href$=.pdf])")
      val notNormalizedLinks = nodes.map(element => {
        val attr = element.attributes.find(_.getKey=="href").get
        val tr = element.parents().find(_.tag().getName()=="tr").get
        val rank = tr.children().head.text().trim().dropRight(1).toDouble
        val points = tr.nextElementSibling().select("td.subtext span").text().trim().takeWhile(_.isDigit).toDouble
        val rankPoints = (2+nodes.length-rank)*(500.0/nodes.length)
        val weight = rankPoints*rankPoints+points
        Link(attr.getValue, weight, element.text())
      }).toList.sortBy(-_.weight).take(10)
      val sum = notNormalizedLinks.map(_.weight).foldLeft(0.0)((a, b)=>a+b)
      notNormalizedLinks.map(element => element.copy(weight = element.weight/sum))
    })
  }
}

/*
// some exception when trying to use this, Mmh, sounds like I found a bug?
object TestRetriever extends LinksRetriever {
  val datas = Range(1,7).toList.map(n => Link("http://localhost:9000/public/images/test/0"+n+".png", n) )
  def getLinks(): Promise[List[Link]] = Promise.pure(datas)
}
*/
