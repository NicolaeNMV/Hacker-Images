package models

import play.api.cache.BasicCache
import play.api._
import play.api.libs._
import play.api.libs.ws.Response
import play.api.libs.concurrent._


/**
 * LinksFetcher handle WS and cache for a LinksExtractor
 */
object LinksFetcher {
  // Fetch a LinksExtractor lazily (with cache)
  def fetch(implicit r:LinksExtractor): Promise[List[Link]] =
    cacheValue.map(Promise.pure(_)).getOrElse(retrieve)
  
  // Retrieve Links from a LinksExtractor (without cache)
  def retrieve(implicit r: LinksExtractor) : Promise[List[Link]] = {
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
  def cacheValue(implicit r:LinksExtractor): Option[List[Link]] = cache.get[List[Link]](r.url)
  // Set the cache value
  def cacheValue(links:List[Link])(implicit r:LinksExtractor):List[Link] = {
    cache.set(r.url, links, r.cacheExpirationSeconds)
    links
  }
  val cache = new BasicCache()
}
