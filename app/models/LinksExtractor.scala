package models

import play.api._
import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.libs._
import play.api.libs.ws.Response
import play.api.Play.current

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._
import java.net.URI
import java.util.Date
import scala.xml._

/*
 * A LinksExtractor retrieves a set of links and weights from a WS response.
 */
trait LinksExtractor {
  val url: String
  def getLinks(response: Response): List[Link]
  val cacheExpirationSeconds: Int
  
  lazy val domain = new URI(url).getHost
  lazy val expirationFromDomain = Play.configuration.getInt("cache.url.for."+domain)
}

case class Link (
  url: String, 
  weight: Double, 
  title: String, 
  feedbackLink: String, 
  feedbackText: String
)

case class RedditRetriever(path: String) extends LinksExtractor {
  val url = "http://www.reddit.com"+path+".json"
  val cacheExpirationSeconds = Play.configuration.getInt("cache.url.for.reddit.com").getOrElse(20)
  
  def getLinks(r: Response) : List[Link] = {
    val json = r.body
    Json.parse(json) \\ "data" collect(_ match {
      case o:JsObject if (List("url", "title", "permalink", "ups", "num_comments").forall(o.value contains _)) => 
        val m = o.value
        Link(
          m("url") match { case JsString(value) => value },
          m("ups") match { case JsNumber(value) => value.doubleValue },
          m("title") match { case JsString(value) => value },
          "http://www.reddit.com" + (m("permalink") match { case JsString(value) => value }),
          (m("num_comments") match { case JsNumber(value) => value.intValue })+" comments"
        )
    }) toList
  }
}

object RedditRootRetriever extends RedditRetriever("/")

case class RssRetriever(url: String) extends LinksExtractor {
  val defaultExpiration = Play.configuration.getInt("cache.url.for.rss").getOrElse(60)
  val cacheExpirationSeconds = expirationFromDomain.getOrElse(defaultExpiration)

  def getLinks(response: Response): List[Link] = {
    val xml = response.xml
    val now = new Date().getTime
    val linkAndMillis = xml \\ "rss" \ "channel" \ "item" map { item =>
      val millisAgo = now - new Date((item \ "pubDate").text).getTime
      val comments = (item \ "comments").text
      val link = Link(
        item \ "link" text,
        1.0,
        item \ "title" text,
        comments,
        if(comments.length>0) "Comments" else ""
      )
      (link, millisAgo)
    } toList
    val sumMillis = linkAndMillis.map(_._2).foldLeft (0L) ( (a, b) => (a+b) ).toDouble
    val maxMillis = linkAndMillis.map(_._2).foldLeft (0L) ( (a, b) => Math.max(a, b) ).toDouble
    linkAndMillis.map { tuple =>
      val link = tuple._1
      val millis = tuple._2.toDouble
      link.copy(weight = (3600000.0+maxMillis-millis)/sumMillis)
    }
  }
}

object PlayFrameworkRssRetriever extends RssRetriever("http://www.playframework.org/community/planet.rss")

object GoogleNewsRetriever extends RssRetriever("http://news.google.com/news?output=rss")


/**
 * HackerNews implementation
 */
object HackerNewsRetriever extends LinksExtractor {
  val url = "http://news.ycombinator.com/news"
  val cacheExpirationSeconds = Play.configuration.getInt("cache.url.for.news.ycombinator.com").getOrElse(10)
  
  // as you can see, HackerNews is damn hard to parse
  def getLinks(r:Response): List[Link] = {
    val html = r.body
    val doc = Jsoup.parse(html);
    val nodes = doc.select("td.title a[href^=http]:not([href$=.pdf])");
    nodes.map(element => {
      val attr = element.attributes.find(_.getKey=="href").get
      val tr = element.parents().find(_.tag().getName()=="tr").get
      val tr2 = tr.nextElementSibling()
      val aItem = tr2.select("a[href^=item]").headOption
      val itemText = aItem.map(_.text())
      val itemHref = aItem.flatMap(_.attributes.find(_.getKey=="href")).
                     map(_.getValue).
                    map(new URI(url).resolve(_).toString())
      val rankStr = tr.children().head.text().trim().dropRight(1)
      val pointsStr = tr2.select("td.subtext span").text().trim().takeWhile(_.isDigit)
      val rankPoints = if(rankStr.isEmpty) 0.0 else 2+nodes.length-rankStr.toDouble
      val points = if(pointsStr.isEmpty) 0.0 else pointsStr.toDouble
      val weight = rankPoints*rankPoints+points
      Link(attr.getValue, weight, element.text(), itemHref.getOrElse(""), itemText.getOrElse(""))
    }).toList.filter(_.weight>0.0).sortBy(-_.weight);
  }
}