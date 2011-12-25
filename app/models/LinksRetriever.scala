package models

import play.api._
import play.api.libs.concurrent._
import play.api.cache.BasicCache
import play.api.Play.current

import com.ning.http.client.Response

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._
import java.net.URI

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

/**
 * HackerNews implementation
 */
object HackerNewsRetriever extends LinksRetriever {
  
  val baseUrl = "http://news.ycombinator.com/news"
  // TODO: move the cache to the controller side (top level)
  val cache = new BasicCache()
  val cacheKey = "models.HackerNewsRetriever.cacheKey"
  val expirationSeconds = Play.configuration.getInt("cache.url.for.news.ycombinator.com").getOrElse(10)

  def getLinks(): Promise[List[Link]] = {
    cache.get[List[Link]](cacheKey).map(Promise.pure(_)).getOrElse({
      // TODO: reduce the code bellow, we should not care about exceptions (handle it on top level with play)
      WS.url(baseUrl).get().extend(promise => {
        promise.value match {
          case Redeemed(response) => {
            Logger.debug(baseUrl+" getted.");
            val links = getLinksFromHtml(response.body);
            cache.set(cacheKey, links, expirationSeconds)
            links
          }
          case Thrown(e:Exception) => {
            Logger.error("HackerNews unable to retrieved ("+e.getMessage()+")");
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
