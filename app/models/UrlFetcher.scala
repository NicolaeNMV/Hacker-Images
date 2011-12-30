package models

import play.api.cache.BasicCache
import play.api._
import play.api.libs._
import play.api.libs.ws.Response
import play.api.libs.concurrent._


/**
 * UrlFetcher handle WS and cache for LinksRetriever
 */
object UrlFetcher {
  // Fetch a LinksRetriever lazily (with cache)
  def fetch(implicit r:LinksRetriever): Promise[List[Link]] =
    cacheValue.map(Promise.pure(_)).getOrElse(retrieve(r))
  
  // Retrieve Links from a LinksRetriever (without cache)
  def retrieve(implicit r: LinksRetriever) : Promise[List[Link]] = {
    WS.url(r.url).get().extend(_.value match {
      case Redeemed(response) => cacheValue(r.getLinks(response))
      case Thrown(e:Exception) => {
        Logger.error(r+" for "+r.url+" was unable to retrieved ("+e.getMessage+")")
        e.printStackTrace()
        Nil
      }
    })
  }

  // Get a cache value
  def cacheValue(implicit r:LinksRetriever): Option[List[Link]] = cache.get[List[Link]](r.url)
  // Set the cache value
  def cacheValue(links:List[Link])(implicit r:LinksRetriever):List[Link] = {
    cache.set(r.url, links, r.cacheExpirationSeconds)
    links
  }
  val cache = new BasicCache()
}
