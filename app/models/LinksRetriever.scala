package models

import play.api._
import play.api.libs.concurrent.{Promise}

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._

case class Link(url: String, weight: Double)

trait LinksRetriever {
  def getLinks(): Promise[List[Link]]
}


object HackerNewsRetriever extends LinksRetriever {
  val url = "http://news.ycombinator.com/news";
  def getLinks(): Promise[List[Link]] = {
    WS.url(url).get().map(response => {
      Logger.debug(url+" getted.");
      val doc = Jsoup.parse(response.getResponseBody);
      val list = doc.select("td.title a[href^=http]:not([href$=.pdf])")
      list.map(element => {
        val attr = element.attributes.find(_.getKey=="href").get
        val tr = element.parents().find(_.tag().getName()=="tr").get
        val rank = tr.children().head.text().trim().dropRight(1).toDouble
        //val points = tr.nextElementSibling().select("td.subtext span").text().trim().takeWhile(_.isDigit).toDouble
        val weight = 2+list.length-rank
        Link(attr.getValue, weight)
      }).toList.sortBy(-_.weight)
    })
  }
}
