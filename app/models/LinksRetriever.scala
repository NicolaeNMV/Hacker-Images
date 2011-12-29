package models

import play.api._
import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.libs._
import play.api.cache.BasicCache
import play.api.Play.current

import com.ning.http.client.Response

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._
import java.net.URI
import java.util.Date
import scala.xml._

/**
 * A LinksRetriever retrieves a set of links and weights from any source.
 */
trait LinksRetriever {
  def getLinks(): Promise[List[Link]]
}

case class Link (
  url: String, 
  weight: Double, 
  title: String, 
  feedbackLink: String, 
  feedbackText: String
)

object RedditRetriever {
  val cache = new BasicCache()
  val expirationSeconds = Play.configuration.getInt("cache.url.for.reddit.com").getOrElse(20)
}
case class RedditRetriever(path: String) extends LinksRetriever {
  import RedditRetriever._

  val url = "http://www.reddit.com"+path+".json"

  def getLinks(): Promise[List[Link]] = {
    cache.get[List[Link]](url).map(Promise.pure(_)).getOrElse({
      WS.url(url).get().extend(_.value match {
        case Redeemed(response) => {
          val links = getLinksFromJson(response.body)
          cache.set(url, links, expirationSeconds)
          links
        }
        case Thrown(e:Exception) => {
          Logger.error("Reddit "+url+" was unable to retrieved ("+e.getMessage+")")
          e.printStackTrace()
          Nil
        }
      })
    })
  }

  def getLinksFromJson(json: String) : List[Link] = {
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


object RssRetriever {
  val cache = new BasicCache()
  val defaultExpiration = Play.configuration.getInt("cache.url.for.rss").getOrElse(60)
}
class RssRetriever(url: String) extends LinksRetriever {
  import RssRetriever._
  val domain = new URI(url).getHost
  val expirationSeconds = Play.configuration.getInt("cache.url.for."+domain).getOrElse(defaultExpiration)

  def getLinks(): Promise[List[Link]] = {
    cache.get[List[Link]](url).map(Promise.pure(_)).getOrElse({
      WS.url(url).get().extend(_.value match {
        case Redeemed(response) => {
          val links = getLinksFromRSS(response.xml)
          cache.set(url, links, expirationSeconds)
          links
        }
        case Thrown(e:Exception) => {
          Logger.error("RSS "+url+" was unable to retrieved ("+e.getMessage+")")
          e.printStackTrace()
          Nil
        }
      })
    })
  }

  def getLinksFromRSS(xml: Elem): List[Link] = {
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
object HackerNewsRetriever extends LinksRetriever {
  
  val baseUrl = "http://news.ycombinator.com/news"
  // TODO: move the cache to the controller side (top level)
  val cache = new BasicCache()
  val expirationSeconds = Play.configuration.getInt("cache.url.for.news.ycombinator.com").getOrElse(10)

  def getLinks(): Promise[List[Link]] = {
    cache.get[List[Link]](baseUrl).map(Promise.pure(_)).getOrElse({
      // TODO: reduce the code bellow, we should not care about exceptions (handle it on top level with play)
      WS.url(baseUrl).get().extend(promise => {
        promise.value match {
          case Redeemed(response) => {
            Logger.debug(baseUrl+" getted.");
            val links = getLinksFromHtml(response.body);
            cache.set(baseUrl, links, expirationSeconds)
            links
          }
          case Thrown(e:Exception) => {
            Logger.error("HackerNews was unable to retrieved ("+e.getMessage()+")");
            e.printStackTrace()
            Nil
          }
        }
      })
    })
  }

  def getLinksFromHtml(html:String): List[Link] = {
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
                     map(new URI(baseUrl).resolve(_).toString())
      val rankStr = tr.children().head.text().trim().dropRight(1)
      val pointsStr = tr2.select("td.subtext span").text().trim().takeWhile(_.isDigit)
      val rankPoints = if(rankStr.isEmpty) 0.0 else 2+nodes.length-rankStr.toDouble
      val points = if(pointsStr.isEmpty) 0.0 else pointsStr.toDouble
      val weight = rankPoints*rankPoints+points
      Link(attr.getValue, weight, element.text(), itemHref.getOrElse(""), itemText.getOrElse(""))
    }).toList.filter(_.weight>0.0).sortBy(-_.weight);
  }
}
